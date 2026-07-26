package dev.hendrikhoemberg.dmhelper.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class TableStateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TableStateWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;
    private final TablePresentationService presentationService;

    public TableStateWebSocketHandler(ObjectMapper objectMapper,
                                      TablePresentationService presentationService) {
        this.objectMapper = objectMapper;
        this.presentationService = presentationService;

        presentationService.setOnStateChange(this::broadcastState);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Player view connected: {} (total: {})", session.getId(), sessions.size());

        try {
            LiveTableState state = presentationService.getCurrentState();
            String json = objectMapper.writeValueAsString(state);
            sendIfOpen(session, new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send initial state to {}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Player view disconnected: {} (total: {})", session.getId(), sessions.size());
    }

    /** How many table displays are currently attached. Read by the cockpit status cluster. */
    public int connectedCount() {
        return sessions.size();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // client messages are ignored — player view is read-only
    }

    private void broadcastState() {
        if (sessions.isEmpty()) return;
        try {
            LiveTableState state = presentationService.getCurrentState();
            String json = objectMapper.writeValueAsString(state);
            TextMessage msg = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                try {
                    sendIfOpen(session, msg);
                } catch (IOException e) {
                    log.warn("Failed to send to session {}", session.getId());
                    sessions.remove(session);
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize table state", e);
        }
    }

    private void sendIfOpen(WebSocketSession session, TextMessage message) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        }
    }
}
