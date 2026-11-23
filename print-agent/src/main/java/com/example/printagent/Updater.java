package com.example.printagent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * Автооновлення (docs/Print Agent.md, «Автооновлення»).
 *
 * <ol>
 *   <li>{@link #startBackgroundChecks} — при запуску й далі щогодини питає сервер
 *       {@code https://<сервер>/agent/update/version} (токен у заголовку X-Agent-Token).</li>
 *   <li>Якщо там новіша версія — завантажує {@code /agent/update/jar} у {@code print-agent.new.jar}
 *       поруч, перевіряє, що це справжній jar саме цієї версії.</li>
 *   <li>Запускає новий файл у режимі заміни ({@code --apply-update <pid> <старий jar>}) і завершується.
 *       Новий процес чекає, поки старий закриється (Windows не дає перезаписати відкритий jar),
 *       копіює себе на місце print-agent.jar і запускає програму звичайно, з тими самими аргументами.</li>
 * </ol>
 * Під час друку оновлення чекає ({@link PrintAgentMain#PRINT_LOCK}).
 */
final class Updater {

    static final String APPLY_FLAG = "--apply-update";
    private static final String NEW_JAR = "print-agent.new.jar";
    private static final Duration CHECK_EVERY = Duration.ofHours(1);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private Updater() {
    }

    /** Версія цієї програми з manifest; «dev», якщо запущено не з jar (з IDE). */
    static String currentVersion() {
        String v = Updater.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /** Файл jar, з якого запущено програму, або порожньо (запуск з IDE). */
    static Optional<Path> runningJar() {
        try {
            Path p = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) && p.toString().endsWith(".jar") ? Optional.of(p) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static void startBackgroundChecks(String[] args) {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    checkOnce(args);
                } catch (Exception e) {
                    System.err.println("[print-agent] Перевірка оновлення не вдалась: " + e.getMessage());
                }
                try {
                    Thread.sleep(CHECK_EVERY.toMillis());
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "print-agent-updater");
        t.setDaemon(true);
        t.start();
    }

    private static void checkOnce(String[] args) throws Exception {
        PrintAgentConfig config = PrintAgentMain.config();
        Optional<Path> jar = runningJar();
        if (config == null || config.token().isBlank() || jar.isEmpty() || "dev".equals(currentVersion())) {
            return;
        }
        String base = httpBase(config.backendWsUrl());
        String remote = get(base + "/agent/update/version", config.token(), HttpResponse.BodyHandlers.ofString()).trim();
        if (compare(remote, currentVersion()) <= 0) {
            return;
        }
        System.out.println("[print-agent] Є нова версія " + remote + " (зараз " + currentVersion() + ") — завантажую…");
        Path target = jar.get().resolveSibling(NEW_JAR);
        Path partial = jar.get().resolveSibling(NEW_JAR + ".part");
        byte[] bytes = get(base + "/agent/update/jar", config.token(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(partial, bytes);
        String downloaded = jarVersion(partial);
        if (!remote.equals(downloaded)) {
            Files.deleteIfExists(partial);
            throw new IOException("завантажений файл має версію " + downloaded + ", очікувалась " + remote);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[print-agent] Оновлюю до версії " + remote + " — програма перезапуститься…");
        restartInto(target, jar.get(), args);
    }

    /** Запускає новий jar у режимі заміни й завершує цей процес (між наклейками). */
    private static void restartInto(Path newJar, Path oldJar, String[] args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable());
        cmd.add("-cp");
        cmd.add(newJar.toString());
        cmd.add(PrintAgentMain.class.getName());
        cmd.add(APPLY_FLAG);
        cmd.add(Long.toString(ProcessHandle.current().pid()));
        cmd.add(oldJar.toString());
        cmd.addAll(List.of(args));
        synchronized (PrintAgentMain.PRINT_LOCK) {
            new ProcessBuilder(cmd).directory(oldJar.getParent().toFile()).inheritIO().start();
            System.exit(0);
        }
    }

    /**
     * Режим заміни: {@code --apply-update <pid старого процесу> <шлях до print-agent.jar> [аргументи]}.
     * Чекає завершення старого процесу, копіює новий jar на місце старого, запускає програму звичайно.
     */
    static void applyUpdate(String[] args) throws Exception {
        long oldPid = Long.parseLong(args[1]);
        Path oldJar = Path.of(args[2]);
        String[] rest = java.util.Arrays.copyOfRange(args, 3, args.length);
        ProcessHandle.of(oldPid).ifPresent(p -> p.onExit().join());
        Path self = runningJar().orElseThrow(() -> new IOException("не знайдено новий jar"));
        IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) { // Windows може ще кілька секунд тримати файл
            try {
                Files.copy(self, oldJar, StandardCopyOption.REPLACE_EXISTING);
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                Thread.sleep(500);
            }
        }
        if (last != null) {
            throw last;
        }
        List<String> cmd = new ArrayList<>(List.of(javaExecutable(), "-jar", oldJar.toString()));
        cmd.addAll(List.of(rest));
        new ProcessBuilder(cmd).directory(oldJar.getParent().toFile()).inheritIO().start();
    }

    /** Прибирає print-agent.new.jar після успішного оновлення (його вже скопійовано на місце). */
    static void cleanUpLeftovers() {
        runningJar().ifPresent(jar -> {
            try {
                Files.deleteIfExists(jar.resolveSibling(NEW_JAR));
                Files.deleteIfExists(jar.resolveSibling(NEW_JAR + ".part"));
            } catch (IOException ignored) {
                // ще зайнятий — приберемо наступного разу
            }
        });
    }

    /** wss://host/ws/print-agent → https://host */
    static String httpBase(String wsUrl) {
        URI uri = URI.create(wsUrl);
        String scheme = "wss".equalsIgnoreCase(uri.getScheme()) ? "https" : "http";
        return scheme + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    /** Порівняння «1.2.10» > «1.2.9»; нечислові частини — як 0. */
    static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? number(x[i]) : 0;
            int yi = i < y.length ? number(y[i]) : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String jarVersion(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getManifest() == null ? null : file.getManifest().getMainAttributes().getValue("Implementation-Version");
        }
    }

    private static <T> T get(String url, String token, HttpResponse.BodyHandler<T> handler) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("X-Agent-Token", token)
                .header("X-Agent-Version", currentVersion())
                .GET().build();
        HttpResponse<T> response = HTTP.send(request, handler);
        if (response.statusCode() != 200) {
            throw new IOException("сервер відповів " + response.statusCode() + " на " + url);
        }
        return response.body();
    }

    /** Той самий java/javaw, яким запущено програму (без вікна консолі, якщо це javaw). */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "javaw").toString());
    }
}
