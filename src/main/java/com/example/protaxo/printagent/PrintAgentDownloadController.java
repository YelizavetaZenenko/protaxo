package com.example.protaxo.printagent;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Завантаження програми друку наклейок з сайту (docs/Print Agent.md, «Основна інформація»).
 * print-agent.jar збирається разом із сервером у Docker-образі й лежить у /app/downloads, тож на
 * сайті завжди версія з останнього деплою.
 * <ul>
 *   <li>{@code /downloads/print-agent.jar} — лише програма: оновлення наявної установки;</li>
 *   <li>{@code /downloads/print-agent.zip} — повний комплект для нового комп'ютера: програма, файл
 *       запуску, приклад налаштувань з адресою цього сервера (токен порожній — він секретний) та
 *       інструкція.</li>
 * </ul>
 * Доступ — як до решти сайту для адміністратора й майстра; кожне завантаження потрапляє в журнал дій.
 */
@Controller
@RequestMapping("/downloads")
public class PrintAgentDownloadController {

    static final String LAUNCHER_NAME = "Запустити друк наклейок.bat";

    private final PrintAgentDistribution distribution;

    public PrintAgentDownloadController(PrintAgentDistribution distribution) {
        this.distribution = distribution;
    }

    @GetMapping("/print-agent.jar")
    public ResponseEntity<byte[]> jar() throws IOException {
        if (!distribution.available()) {
            return ResponseEntity.notFound().build();
        }
        return file("print-agent.jar", MediaType.parseMediaType("application/java-archive"), distribution.jarBytes());
    }

    @GetMapping("/print-agent.zip")
    public ResponseEntity<byte[]> zip(HttpServletRequest request) throws IOException {
        if (!distribution.available()) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "ProTaxo-PrintAgent/print-agent.jar", distribution.jarBytes());
            put(zip, "ProTaxo-PrintAgent/" + LAUNCHER_NAME, launcher().getBytes(StandardCharsets.US_ASCII));
            put(zip, "ProTaxo-PrintAgent/printagent.properties.example",
                    distribution.exampleProperties(webSocketUrl(request)).getBytes(StandardCharsets.UTF_8));
            put(zip, "ProTaxo-PrintAgent/Як встановити.txt", withBom(instructions()));
        }
        return file("ProTaxo-PrintAgent.zip", MediaType.parseMediaType("application/zip"), out.toByteArray());
    }

    /** wss://адреса-цього-сайту/ws/print-agent — щоб у прикладі налаштувань була правильна адреса. */
    static String webSocketUrl(HttpServletRequest request) {
        String scheme = "https".equalsIgnoreCase(request.getScheme()) ? "wss" : "ws";
        int port = request.getServerPort();
        boolean defaultPort = port <= 0 || (scheme.equals("wss") && port == 443) || (scheme.equals("ws") && port == 80);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + "/ws/print-agent";
    }

    /**
     * Запуск без консолі. Лише ASCII (cmd.exe читає .bat у кодовій сторінці OEM). Шукає javaw у PATH,
     * інакше — у типових папках JDK/JRE (Oracle, Adoptium).
     */
    static String launcher() {
        return String.join("\r\n",
                "@echo off",
                "cd /d \"%~dp0\"",
                "set JAVAW=",
                "for %%J in (javaw.exe) do set JAVAW=%%~$PATH:J",
                "if not defined JAVAW for /d %%D in (\"%ProgramFiles%\\Java\\jdk-*\" \"%ProgramFiles%\\Java\\jre-*\" \"%ProgramFiles%\\Eclipse Adoptium\\jdk-*\" \"%ProgramFiles%\\Eclipse Adoptium\\jre-*\") do if exist \"%%~D\\bin\\javaw.exe\" set JAVAW=%%~D\\bin\\javaw.exe",
                "if not defined JAVAW (",
                "  echo Java not found. Install Java 21 or newer: https://adoptium.net",
                "  pause",
                "  exit /b 1",
                ")",
                "start \"\" \"%JAVAW%\" -jar print-agent.jar printagent.properties",
                "");
    }

    static String instructions() {
        return String.join("\r\n",
                "ProTaxo — програма друку наклейок",
                "",
                "ОНОВЛЕННЯ (програма вже стоїть):",
                "  1. Закрийте вікно «ProTaxo — друк наклейок».",
                "  2. Замініть файл print-agent.jar у папці програми новим (решту файлів не чіпайте —",
                "     printagent.properties з токеном і налаштуваннями принтера лишається).",
                "  3. Запустіть «" + LAUNCHER_NAME + "».",
                "",
                "НОВЕ ВСТАНОВЛЕННЯ:",
                "  1. Потрібна Java 21 або новіша (https://adoptium.net).",
                "  2. Розпакуйте папку ProTaxo-PrintAgent, наприклад у D:\\.",
                "  3. Перейменуйте printagent.properties.example на printagent.properties.",
                "  4. Відкрийте його Блокнотом і впишіть token= (токен дає адміністратор, він секретний).",
                "  5. Запустіть «" + LAUNCHER_NAME + "». У вікні програми перевірте принтер і розміри",
                "     наклейки й натисніть «Зберегти».",
                "",
                "Працює одна копія програми на комп'ютер. Якщо на сайті «Print Agent не підключений» —",
                "програму не запущено або немає інтернету.",
                "");
    }

    private static byte[] withBom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[body.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(body, 0, result, 3, body.length);
        return result;
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static ResponseEntity<byte[]> file(String name, MediaType type, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(type)
                .body(body);
    }
}
