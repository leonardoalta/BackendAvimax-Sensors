package com.avimax.backend.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketSessions {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessions.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public void add(WebSocketSession session) {
        sessions.add(session);
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session);
    }

    public void broadcast(String payload) {
        TextMessage msg = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(msg);
                }
            } catch (IOException e) {
                log.warn("Error enviando mensaje WS a {}", s.getId(), e);
            }
        }
    }

    public int count() {
        return sessions.size();
    }
}
