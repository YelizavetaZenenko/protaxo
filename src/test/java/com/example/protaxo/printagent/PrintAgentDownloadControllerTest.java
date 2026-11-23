package com.example.protaxo.printagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

/** Завантаження програми друку наклейок з сайту: дата збірки, повний комплект без токена. */
class PrintAgentDownloadControllerTest {

    @TempDir Path dir;

    private PrintAgentDownloadController controller() throws IOException {
        Path jar = dir.resolve("print-agent.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Build-Time", "2026-11-23 10:42");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream ignored = new JarOutputStream(out, manifest)) {
            // порожній jar з manifest
        }
        Path example = dir.resolve("printagent.properties.example");
        Files.writeString(example, "# приклад\nbackend.ws.url=ws://localhost:8080/ws/print-agent\ntoken=dev-print-agent-token\nprinter.name=Godex G500\n");
        return new PrintAgentDownloadController(new PrintAgentDistribution(jar.toString(), example.toString()));
    }

    @Test
    void buildTimeIsShownWhenJarExists() throws IOException {
        controller();
        assertThat(new PrintAgentDistribution(dir.resolve("print-agent.jar").toString(), "x").buildTime()).isPresent();
        assertThat(new PrintAgentDistribution(dir.resolve("none.jar").toString(), "x").buildTime()).isEmpty();
        assertThat(PrintAgentDistribution.localTime("не дата")).isEqualTo("не дата");
    }

    @Test
    void zipHasEverythingButTheToken() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("protaxo.example.ts.net");
        request.setServerPort(443);
        byte[] zip = controller().zip(request).getBody();

        Map<String, String> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                files.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(files).containsKeys("ProTaxo-PrintAgent/print-agent.jar",
                "ProTaxo-PrintAgent/" + PrintAgentDownloadController.LAUNCHER_NAME,
                "ProTaxo-PrintAgent/printagent.properties.example",
                "ProTaxo-PrintAgent/Як встановити.txt");
        String props = files.get("ProTaxo-PrintAgent/printagent.properties.example");
        assertThat(props).contains("backend.ws.url=wss://protaxo.example.ts.net/ws/print-agent")
                .contains("\ntoken=\n").doesNotContain("dev-print-agent-token");
        String bat = files.get("ProTaxo-PrintAgent/" + PrintAgentDownloadController.LAUNCHER_NAME);
        assertThat(bat).contains("javaw").contains("print-agent.jar printagent.properties");
        assertThat(bat.chars().allMatch(c -> c < 128)).isTrue();
    }
}
