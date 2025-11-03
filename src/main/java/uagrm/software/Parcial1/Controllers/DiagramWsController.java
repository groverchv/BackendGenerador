package uagrm.software.Parcial1.Controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import uagrm.software.Parcial1.Models.DiagramEntity;
import uagrm.software.Parcial1.Models.ProjectEntity;
import uagrm.software.Parcial1.Repository.ProjectRepository;
import uagrm.software.Parcial1.Services.PresenceService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WebSocket/STOMP "sockend" para edición en vivo de diagramas + presencia.
 * Sin DTO: los mensajes entran/salen como Map<String, Object>.
 *
 * Canales:
 *  - Cliente -> Servidor (publish):
 *      /app/projects/{projectId}/update     (persistente: guarda y broadcastea snapshot)
 *      /app/projects/{projectId}/cursor     (efímero: posición durante drag)
 *      /app/projects/{projectId}/presence.enter
 *      /app/projects/{projectId}/presence.leave
 *
 *  - Servidor -> Clientes (subscribe):
 *      /topic/projects/{projectId}          (snapshots)
 *      /topic/projects/{projectId}/cursors  (movimientos efímeros)
 *      /topic/projects/{projectId}/presence (conteo de conectados)
 *      /user/queue/errors                   (errores específicos del usuario)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DiagramWsController {

    private final ProjectRepository projectRepository;
    private final SimpMessagingTemplate messaging;
    private final PresenceService presenceService; // contador de conexiones por proyecto
    
    // Constantes para validación
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_JSON_LENGTH = 5_000_000; // 5MB aprox
    private static final long RATE_LIMIT_MILLIS = 100; // mínimo 100ms entre updates del mismo cliente

    /* =========================================================
     *                EDICIÓN / SNAPSHOT PERSISTENTE
     * ========================================================= */

    /**
     * Recibe un "patch" completo del cliente y lo guarda.
     * Payload esperado (ejemplo):
     * {
     *   "clientId": "uuid",
     *   "baseVersion": 12,
     *   "name": "Principal",
     *   "nodes": "[...]",     // String JSON
     *   "edges": "[...]",     // String JSON
     *   "viewport": "{...}"   // (opcional) String JSON
     * }
     *
     * Emite a /topic/projects/{id} con:
     * { projectId, clientId, name, nodes, edges, viewport, version, serverTs, conflict }
     */
    @MessageMapping("/projects/{projectId}/update")
    @Transactional
    public void onUpdate(@DestinationVariable Long projectId,
                         @Payload Map<String, Object> payload,
                         @Header("simpSessionId") String sessionId) {

        try {
            // Validaciones de entrada
            validatePayload(payload, sessionId);
            
            // Rate limiting
            if (!presenceService.checkRateLimit(sessionId, RATE_LIMIT_MILLIS)) {
                log.warn("Rate limit excedido para sessionId: {} en proyecto: {}", sessionId, projectId);
                throw new IllegalStateException("Demasiadas actualizaciones. Por favor, espera un momento.");
            }

            ProjectEntity p = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado: " + projectId));

            DiagramEntity d = p.getDiagram();
            if (d == null) {
                throw new IllegalStateException("Diagrama no encontrado para proyecto " + projectId);
            }

            String clientId = asString(payload.get("clientId"));
            Integer baseVers = asInteger(payload.get("baseVersion"));
            String name      = asString(payload.get("name"));
            String nodes     = asString(payload.get("nodes"));
            String edges     = asString(payload.get("edges"));
            String viewport  = asString(payload.get("viewport"));

            // Validaciones adicionales
            if (name != null && name.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Nombre demasiado largo (max " + MAX_NAME_LENGTH + " caracteres)");
            }
            if (nodes != null && nodes.length() > MAX_JSON_LENGTH) {
                throw new IllegalArgumentException("Datos de nodos demasiado grandes");
            }
            if (edges != null && edges.length() > MAX_JSON_LENGTH) {
                throw new IllegalArgumentException("Datos de aristas demasiado grandes");
            }

            boolean conflict = (baseVers != null && !Objects.equals(baseVers, d.getVersion()));

            if (name != null)     d.setName(name);
            if (nodes != null)    d.setNodes(nodes);
            if (edges != null)    d.setEdges(edges);
            if (viewport != null) d.setViewport(viewport);

            // Guardar y forzar flush para obtener nueva @Version
            projectRepository.saveAndFlush(p);

            Map<String, Object> evt = new HashMap<>();
            evt.put("projectId", projectId);
            evt.put("clientId", clientId);
            evt.put("name", d.getName());
            evt.put("nodes", d.getNodes());
            evt.put("edges", d.getEdges());
            evt.put("viewport", d.getViewport());
            evt.put("version", d.getVersion());
            evt.put("serverTs", System.currentTimeMillis());
            evt.put("conflict", conflict);
            evt.put("success", true);

            messaging.convertAndSend("/topic/projects/" + projectId, evt);
            
            log.debug("Diagrama actualizado - proyecto: {}, versión: {}, conflicto: {}", 
                     projectId, d.getVersion(), conflict);

        } catch (Exception e) {
            log.error("Error en onUpdate - proyecto: {}, sessionId: {}", projectId, sessionId, e);
            throw e; // Se manejará en @MessageExceptionHandler
        }
    }

    /* =========================================================
     *                    MOVIMIENTO EN VIVO
     * ========================================================= */

    /**
     * Rebota sin persistir. Útil para posiciones en tiempo real durante el drag.
     * Payload típico:
     * { "type":"diagram.move", "clientId":"...", "id":"nodeId", "x": 120.5, "y": 60.0, "ts": 1234567, "seq": 42 }
     */
    @MessageMapping("/projects/{projectId}/cursor")
    public void onCursor(@DestinationVariable Long projectId,
                         @Payload Map<String, Object> payload,
                         @Header("simpSessionId") String sessionId) {

        try {
            // Validación básica
            if (payload == null || payload.isEmpty()) {
                log.warn("Payload vacío en cursor - sessionId: {}", sessionId);
                return;
            }

            // Verificar que el usuario está en el proyecto
            if (!presenceService.isInRoom(projectId, sessionId)) {
                log.warn("Usuario no está en la sala - sessionId: {}, proyecto: {}", sessionId, projectId);
                return;
            }

            // Asegura timestamp del servidor
            payload.putIfAbsent("serverTs", System.currentTimeMillis());
            messaging.convertAndSend("/topic/projects/" + projectId + "/cursors", payload);
            
        } catch (Exception e) {
            log.error("Error en onCursor - proyecto: {}, sessionId: {}", projectId, sessionId, e);
            // No lanzamos excepción para no interrumpir el flujo de cursores
        }
    }

    /* =========================================================
     *                          PRESENCIA
     * ========================================================= */

    private void broadcastPresence(Long projectId, int online, String action) {
        try {
            Map<String, Object> evt = new HashMap<>();
            evt.put("__system", "presence");
            evt.put("action", action);
            evt.put("projectId", projectId);
            evt.put("online", online);
            evt.put("serverTs", System.currentTimeMillis());
            messaging.convertAndSend("/topic/projects/" + projectId + "/presence", evt);
            log.debug("Presencia broadcast - proyecto: {}, online: {}, acción: {}", projectId, online, action);
        } catch (Exception e) {
            log.error("Error broadcasting presencia - proyecto: {}", projectId, e);
        }
    }

    /** Cliente anuncia que entra a la "sala" del proyecto. */
    @MessageMapping("/projects/{projectId}/presence.enter")
    public void presenceEnter(@DestinationVariable Long projectId,
                              @Header("simpSessionId") String sessionId) {
        try {
            // Verificar que el proyecto existe
            if (!projectRepository.existsById(projectId)) {
                throw new IllegalArgumentException("Proyecto no encontrado: " + projectId);
            }

            int online = presenceService.enter(projectId, sessionId);
            broadcastPresence(projectId, online, "enter");
            log.info("Usuario entró - proyecto: {}, sessionId: {}, total online: {}", 
                    projectId, sessionId, online);
                    
        } catch (Exception e) {
            log.error("Error en presenceEnter - proyecto: {}, sessionId: {}", projectId, sessionId, e);
            throw e;
        }
    }

    /** Salida explícita (opcional, el listener de disconnect también cubre el caso). */
    @MessageMapping("/projects/{projectId}/presence.leave")
    public void presenceLeave(@DestinationVariable Long projectId,
                              @Header("simpSessionId") String sessionId) {
        try {
            int online = presenceService.leave(projectId, sessionId);
            broadcastPresence(projectId, online, "leave");
            log.info("Usuario salió - proyecto: {}, sessionId: {}, total online: {}", 
                    projectId, sessionId, online);
                    
        } catch (Exception e) {
            log.error("Error en presenceLeave - proyecto: {}, sessionId: {}", projectId, sessionId, e);
            // No lanzamos excepción porque puede ser una desconexión normal
        }
    }
    
    /**
     * Permite al cliente solicitar el estado actual del diagrama
     */
    @MessageMapping("/projects/{projectId}/sync")
    @Transactional(readOnly = true)
    public void syncDiagram(@DestinationVariable Long projectId,
                           @Header("simpSessionId") String sessionId) {
        try {
            ProjectEntity p = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado: " + projectId));

            DiagramEntity d = p.getDiagram();
            if (d == null) {
                throw new IllegalStateException("Diagrama no encontrado para proyecto " + projectId);
            }

            Map<String, Object> evt = new HashMap<>();
            evt.put("__system", "sync");
            evt.put("projectId", projectId);
            evt.put("name", d.getName());
            evt.put("nodes", d.getNodes());
            evt.put("edges", d.getEdges());
            evt.put("viewport", d.getViewport());
            evt.put("version", d.getVersion());
            evt.put("serverTs", System.currentTimeMillis());

            // Enviar solo al cliente que lo solicitó
            messaging.convertAndSendToUser(sessionId, "/queue/sync", evt);
            log.debug("Sync enviado - proyecto: {}, versión: {}, sessionId: {}", 
                     projectId, d.getVersion(), sessionId);
                     
        } catch (Exception e) {
            log.error("Error en syncDiagram - proyecto: {}, sessionId: {}", projectId, sessionId, e);
            throw e;
        }
    }

    /* =========================================================
     *                         ERRORES
     * ========================================================= */

    /**
     * Manejo centralizado de errores - envía el error al usuario específico
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> onError(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", ex.getClass().getSimpleName());
        err.put("message", ex.getMessage());
        err.put("serverTs", System.currentTimeMillis());
        err.put("timestamp", System.currentTimeMillis());
        
        // Log del error
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            log.warn("Error de validación WebSocket: {}", ex.getMessage());
        } else {
            log.error("Error inesperado en WebSocket", ex);
        }
        
        return err;
    }

    /* =========================================================
     *                        HELPERS
     * ========================================================= */

    /**
     * Valida el payload del mensaje
     */
    private void validatePayload(Map<String, Object> payload, String sessionId) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Payload vacío");
        }
        
        String clientId = asString(payload.get("clientId"));
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId es requerido");
        }
        
        if (clientId.length() > 100) {
            throw new IllegalArgumentException("clientId demasiado largo");
        }
    }

    private static String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private static Integer asInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
