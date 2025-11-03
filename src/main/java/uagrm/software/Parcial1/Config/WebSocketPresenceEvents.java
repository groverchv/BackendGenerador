package uagrm.software.Parcial1.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;
import uagrm.software.Parcial1.Services.PresenceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maneja eventos del ciclo de vida de WebSocket para mejorar la robustez:
 * - Conexión establecida
 * - Desconexión (normal o error)
 * - Suscripciones
 * - Errores de transporte
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceEvents {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messaging;

    /**
     * Evento cuando un cliente se conecta exitosamente
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        log.info("WebSocket conectado - sessionId: {}", sessionId);
        
        // Registrar timestamp de conexión
        presenceService.recordConnection(sessionId);
    }

    /**
     * Evento cuando un cliente se suscribe a un destino
     */
    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String destination = headers.getDestination();
        log.debug("Suscripción - sessionId: {}, destino: {}", sessionId, destination);
    }

    /**
     * Evento cuando un cliente se desconecta (el más importante)
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String closeStatus = event.getCloseStatus() != null ? 
                             event.getCloseStatus().toString() : "UNKNOWN";
        
        log.info("WebSocket desconectado - sessionId: {}, estado: {}", sessionId, closeStatus);
        
        try {
            List<Long> affected = presenceService.removeSessionFromAll(sessionId);
            long ts = System.currentTimeMillis();

            for (Long projectId : affected) {
                int online = presenceService.count(projectId);
                Map<String, Object> evt = new HashMap<>();
                evt.put("__system", "presence");
                evt.put("action", "disconnect");
                evt.put("projectId", projectId);
                evt.put("online", online);
                evt.put("serverTs", ts);
                
                messaging.convertAndSend("/topic/projects/" + projectId + "/presence", evt);
                log.debug("Presencia actualizada para proyecto {}: {} usuarios online", projectId, online);
            }
        } catch (Exception e) {
            log.error("Error manejando desconexión de sessionId: {}", sessionId, e);
        }
    }

    /**
     * Evento cuando ocurre un error en el transporte WebSocket
     */
    @EventListener
    public void handleTransportError(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (event.getCloseStatus() != null && !event.getCloseStatus().equalsCode(1000)) {
            log.warn("Error de transporte WebSocket - sessionId: {}, código: {}", 
                    sessionId, event.getCloseStatus());
        }
    }

    /**
     * Evento cuando se cancela una suscripción
     */
    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        log.debug("Desuscripción - sessionId: {}", sessionId);
    }
}
