package uagrm.software.Parcial1.Services;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class PresenceService {

    // projectId -> set de sessionIds conectadas a ese proyecto
    private final ConcurrentMap<Long, Set<String>> rooms = new ConcurrentHashMap<>();

    public int enter(Long projectId, String sessionId) {
        rooms.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        return count(projectId);
    }

    public int leave(Long projectId, String sessionId) {
        Set<String> set = rooms.get(projectId);
        if (set == null) return 0;
        set.remove(sessionId);
        if (set.isEmpty()) rooms.remove(projectId);
        return count(projectId);
    }

    /** Elimina la sesión en todas las salas (por desconexión). Devuelve los projectIds afectados. */
    public List<Long> removeSessionFromAll(String sessionId) {
        List<Long> affected = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> e : rooms.entrySet()) {
            if (e.getValue().remove(sessionId)) {
                affected.add(e.getKey());
                if (e.getValue().isEmpty()) rooms.remove(e.getKey());
            }
        }
        return affected;
    }

    public int count(Long projectId) {
        return rooms.getOrDefault(projectId, Collections.emptySet()).size();
    }
}
