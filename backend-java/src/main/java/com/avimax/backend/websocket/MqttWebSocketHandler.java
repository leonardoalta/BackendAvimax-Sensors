package com.avimax.backend.websocket;

import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class MqttWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttWebSocketHandler.class);

    private final WebSocketSessions sessions;
    private final ApplicationEventPublisher eventPublisher;

    public MqttWebSocketHandler(WebSocketSessions sessions, ApplicationEventPublisher eventPublisher) {
        this.sessions = sessions;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WS conexión establecida: {} (total={})", session.getId(), sessions.count());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WS conexión cerrada: {} (total={})", session.getId(), sessions.count());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("WS recibido de {}: {}", session.getId(), payload);
        // Forward incoming WS messages as publish requests to the bridge via an application event.
        // Expecting frontend to send JSON: {"topic":"...","payload":{...},"retained":false}
        eventPublisher.publishEvent(new org.springframework.context.ApplicationEvent(payload) {});
    }
}
