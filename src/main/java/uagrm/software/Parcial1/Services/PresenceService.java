package uagrm.software.Parcial1.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Servicio robusto de presencia con:
 * - Tracking de sesiones por proyecto
 * - Rate limiting por sesión
 * - Timestamps de conexión
 * - Limpieza automática de sesiones obsoletas
 */
@Slf4j
@Service
public class PresenceService {

    // projectId -> set de sessionIds conectadas a ese proyecto
    private final ConcurrentMap<Long, Set<String>> rooms = new ConcurrentHashMap<>();
    
    // sessionId -> último timestamp de actualización (para rate limiting)
    private final ConcurrentMap<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    
    // sessionId -> timestamp de conexión
    private final ConcurrentMap<String, Long> connectionTime = new ConcurrentHashMap<>();
    
    // sessionId -> set de projectIds donde está presente
    private final ConcurrentMap<String, Set<Long>> sessionProjects = new ConcurrentHashMap<>();

    /**
     * Registra una nueva conexión
     */
    public void recordConnection(String sessionId) {
        connectionTime.put(sessionId, System.currentTimeMillis());
        log.debug("Conexión registrada: {}", sessionId);
    }

    /**
     * Usuario entra a un proyecto
     */
    public int enter(Long projectId, String sessionId) {
        if (projectId == null || sessionId == null) {
            throw new IllegalArgumentException("projectId y sessionId no pueden ser null");
        }
        
        rooms.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionProjects.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(projectId);
        
        int count = count(projectId);
        log.debug("Usuario entró - proyecto: {}, sessionId: {}, total: {}", projectId, sessionId, count);
        return count;
    }

    /**
     * Usuario sale de un proyecto
     */
    public int leave(Long projectId, String sessionId) {
        if (projectId == null || sessionId == null) {
            return 0;
        }
        
        Set<String> set = rooms.get(projectId);
        if (set != null) {
            set.remove(sessionId);
            if (set.isEmpty()) {
                rooms.remove(projectId);
            }
        }
        
        Set<Long> projects = sessionProjects.get(sessionId);
        if (projects != null) {
            projects.remove(projectId);
            if (projects.isEmpty()) {
                sessionProjects.remove(sessionId);
            }
        }
        
        int count = count(projectId);
        log.debug("Usuario salió - proyecto: {}, sessionId: {}, total: {}", projectId, sessionId, count);
        return count;
    }

    /**
     * Elimina la sesión en todas las salas (por desconexión). 
     * Devuelve los projectIds afectados.
     */
    public List<Long> removeSessionFromAll(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        
        List<Long> affected = new ArrayList<>();
        
        // Remover de todas las salas
        for (Map.Entry<Long, Set<String>> e : rooms.entrySet()) {
            if (e.getValue().remove(sessionId)) {
                affected.add(e.getKey());
                if (e.getValue().isEmpty()) {
                    rooms.remove(e.getKey());
                }
            }
        }
        
        // Limpiar datos de la sesión
        sessionProjects.remove(sessionId);
        lastUpdateTime.remove(sessionId);
        connectionTime.remove(sessionId);
        
        if (!affected.isEmpty()) {
            log.info("Sesión {} removida de {} proyectos", sessionId, affected.size());
        }
        
        return affected;
    }

    /**
     * Cuenta usuarios en un proyecto
     */
    public int count(Long projectId) {
        if (projectId == null) return 0;
        return rooms.getOrDefault(projectId, Collections.emptySet()).size();
    }

    /**
     * Verifica si una sesión está en un proyecto específico
     */
    public boolean isInRoom(Long projectId, String sessionId) {
        if (projectId == null || sessionId == null) return false;
        Set<String> set = rooms.get(projectId);
        return set != null && set.contains(sessionId);
    }

    /**
     * Rate limiting: verifica si ha pasado suficiente tiempo desde la última actualización
     */
    public boolean checkRateLimit(String sessionId, long minIntervalMillis) {
        if (sessionId == null) return false;
        
        long now = System.currentTimeMillis();
        Long last = lastUpdateTime.get(sessionId);
        
        if (last == null || (now - last) >= minIntervalMillis) {
            lastUpdateTime.put(sessionId, now);
            return true;
        }
        
        return false;
    }

    /**
     * Obtiene el tiempo de conexión de una sesión
     */
    public Long getConnectionTime(String sessionId) {
        return connectionTime.get(sessionId);
    }

    /**
     * Obtiene todos los proyectos donde está presente una sesión
     */
    public Set<Long> getSessionProjects(String sessionId) {
        if (sessionId == null) return Collections.emptySet();
        return sessionProjects.getOrDefault(sessionId, Collections.emptySet());
    }

    /**
     * Obtiene estadísticas de presencia
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRooms", rooms.size());
        stats.put("totalSessions", connectionTime.size());
        stats.put("activeConnections", sessionProjects.size());
        
        int totalUsers = rooms.values().stream()
                .mapToInt(Set::size)
                .sum();
        stats.put("totalUserConnections", totalUsers);
        
        return stats;
    }

    /**
     * Limpieza periódica de sesiones obsoletas (cada 5 minutos)
     * Elimina entradas de sesiones que ya no están en ningún proyecto
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void cleanupObsoleteSessions() {
        long now = System.currentTimeMillis();
        long maxAge = 3600000; // 1 hora
        
        int cleaned = 0;
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : connectionTime.entrySet()) {
            String sessionId = entry.getKey();
            long connTime = entry.getValue();
            
            // Si la sesión no está en ningún proyecto y es antigua
            if (!sessionProjects.containsKey(sessionId) && (now - connTime) > maxAge) {
                toRemove.add(sessionId);
            }
        }
        
        for (String sessionId : toRemove) {
            connectionTime.remove(sessionId);
            lastUpdateTime.remove(sessionId);
            cleaned++;
        }
        
        if (cleaned > 0) {
            log.info("Limpieza automática: {} sesiones obsoletas removidas", cleaned);
        }
    }
}
