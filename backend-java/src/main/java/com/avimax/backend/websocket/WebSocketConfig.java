package com.avimax.backend.websocket;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketSessions webSocketSessions;
    private final ApplicationEventPublisher eventPublisher;

    public WebSocketConfig(WebSocketSessions webSocketSessions, ApplicationEventPublisher eventPublisher) {
        this.webSocketSessions = webSocketSessions;
        this.eventPublisher = eventPublisher;
    }

    @Bean
    public MqttWebSocketHandler mqttWebSocketHandler() {
        return new MqttWebSocketHandler(webSocketSessions, eventPublisher);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mqttWebSocketHandler(), "/ws/mqtt").setAllowedOrigins("*");
    }
}
