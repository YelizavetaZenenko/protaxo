package com.example.protaxo.printagent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.jar.JarFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Файли програми друку наклейок, які віддає сайт (docs/Print Agent.md): print-agent.jar і приклад
 * налаштувань. Збираються разом із сервером у Docker-образі й лежать у /app/downloads.
 */
@Component
public class PrintAgentDistribution {

    private static final DateTimeFormatter BUILD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Path jarPath;
    private final Path examplePath;

    public PrintAgentDistribution(
            @Value("${app.print-agent.jar-path:/app/downloads/print-agent.jar}") String jarPath,
            @Value("${app.print-agent.example-path:/app/downloads/printagent.properties.example}") String examplePath) {
        this.jarPath = Path.of(jarPath);
        this.examplePath = Path.of(examplePath);
    }

    public boolean available() {
        return Files.isRegularFile(jarPath);
    }

    public byte[] jarBytes() throws IOException {
        return Files.readAllBytes(jarPath);
    }

    /** Дата збірки з manifest (Build-Time) у часі сервера; порожньо, якщо файлу немає (локальний запуск). */
    public Optional<String> buildTime() {
        if (!available()) {
            return Optional.empty();
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            String time = jar.getManifest() == null ? null : jar.getManifest().getMainAttributes().getValue("Build-Time");
            return Optional.of(time == null ? "невідомо" : localTime(time));
        } catch (IOException e) {
            return Optional.of("невідомо");
        }
    }

    /** Номер версії програми (Implementation-Version з manifest, напр. «1.0.0»); порожньо, якщо файлу немає. */
    public Optional<String> version() {
        if (!available()) {
            return Optional.empty();
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            return Optional.ofNullable(jar.getManifest() == null ? null
                    : jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Maven пише Build-Time в UTC («2026-11-23 10:42») — показуємо в часовому поясі сервера (Київ). */
    static String localTime(String utc) {
        try {
            return LocalDateTime.parse(utc, BUILD_TIME).atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault()).format(SHOWN);
        } catch (DateTimeParseException e) {
            return utc;
        }
    }

    /** Приклад налаштувань із репозиторію, але з адресою цього сервера й порожнім токеном. */
    public String exampleProperties(String wsUrl) {
        String text;
        try {
            text = Files.isRegularFile(examplePath) ? Files.readString(examplePath, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (text.isEmpty()) {
            text = "backend.ws.url=\ntoken=\nprinter.name=Godex G500\nprinter.language=ezpl\n";
        }
        return text.replaceAll("(?m)^backend\\.ws\\.url=.*$", "backend.ws.url=" + wsUrl)
                .replaceAll("(?m)^token=.*$", "token=");
    }
}
