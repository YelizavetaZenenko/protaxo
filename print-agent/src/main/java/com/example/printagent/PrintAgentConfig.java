package com.example.printagent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loaded from printagent.properties (see printagent.properties.example) — token must match the
 * backend's {@code print-agent.token} (application.properties / PRINT_AGENT_TOKEN env var).
 */
public final class PrintAgentConfig {

    private final String backendWsUrl;
    private final String token;
    private final String printerName;

    private PrintAgentConfig(String backendWsUrl, String token, String printerName) {
        this.backendWsUrl = backendWsUrl;
        this.token = token;
        this.printerName = printerName;
    }

    public static PrintAgentConfig load(Path propertiesPath) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propertiesPath)) {
            props.load(in);
        }
        String backendWsUrl = props.getProperty("backend.ws.url", "ws://localhost:8080/ws/print-agent");
        String token = props.getProperty("token", "");
        String printerName = props.getProperty("printer.name", "");
        if (token.isBlank()) {
            throw new IllegalStateException(
                    "printagent.properties: 'token' не задано — має збігатись із print-agent.token бекенду");
        }
        return new PrintAgentConfig(backendWsUrl, token, printerName);
    }

    public String backendWsUrl() {
        return backendWsUrl;
    }

    public String token() {
        return token;
    }

    /** Empty/blank means "use the OS default printer". */
    public String printerName() {
        return printerName;
    }
}
