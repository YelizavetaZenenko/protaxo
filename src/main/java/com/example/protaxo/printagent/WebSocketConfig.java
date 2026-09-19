package com.example.protaxo.printagent;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PrintAgentWebSocketHandler printAgentWebSocketHandler;
    private final PrintAgentHandshakeInterceptor printAgentHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(printAgentWebSocketHandler, "/ws/print-agent")
                .addInterceptors(printAgentHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
