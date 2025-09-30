package uagrm.software.Parcial1.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
 *      /queue/errors                        (errores generales)
 */
@Controller
@RequiredArgsConstructor
public class DiagramWsController {

    private final ProjectRepository projectRepository;
    private final SimpMessagingTemplate messaging;
    private final PresenceService presenceService; // contador de conexiones por proyecto

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
                         @Payload Map<String, Object> payload) {

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

        messaging.convertAndSend("/topic/projects/" + projectId, evt);
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
                         @Payload Map<String, Object> payload) {

        // Asegura timestamp del servidor (por si el cliente no lo puso)
        payload.putIfAbsent("serverTs", System.currentTimeMillis());
        messaging.convertAndSend("/topic/projects/" + projectId + "/cursors", payload);
    }

    /* =========================================================
     *                          PRESENCIA
     * ========================================================= */

    private void broadcastPresence(Long projectId, int online) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("__system", "presence");
        evt.put("projectId", projectId);
        evt.put("online", online);
        evt.put("serverTs", System.currentTimeMillis());
        messaging.convertAndSend("/topic/projects/" + projectId + "/presence", evt);
    }

    /** Cliente anuncia que entra a la "sala" del proyecto. */
    @MessageMapping("/projects/{projectId}/presence.enter")
    public void presenceEnter(@DestinationVariable Long projectId,
                              @Header("simpSessionId") String sessionId) {
        int online = presenceService.enter(projectId, sessionId);
        broadcastPresence(projectId, online);
    }

    /** Salida explícita (opcional, el listener de disconnect también cubre el caso). */
    @MessageMapping("/projects/{projectId}/presence.leave")
    public void presenceLeave(@DestinationVariable Long projectId,
                              @Header("simpSessionId") String sessionId) {
        int online = presenceService.leave(projectId, sessionId);
        broadcastPresence(projectId, online);
    }

    /* =========================================================
     *                         ERRORES
     * ========================================================= */

    @MessageExceptionHandler
    public void onError(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", ex.getClass().getSimpleName());
        err.put("message", ex.getMessage());
        err.put("serverTs", System.currentTimeMillis());
        messaging.convertAndSend("/queue/errors", err);
    }

    /* =========================================================
     *                        HELPERS
     * ========================================================= */

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
