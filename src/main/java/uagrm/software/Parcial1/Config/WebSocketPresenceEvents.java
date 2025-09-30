package uagrm.software.Parcial1.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import uagrm.software.Parcial1.Services.PresenceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketPresenceEvents implements ApplicationListener<SessionDisconnectEvent> {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messaging;

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        List<Long> affected = presenceService.removeSessionFromAll(sessionId);
        long ts = System.currentTimeMillis();

        for (Long projectId : affected) {
            int online = presenceService.count(projectId);
            Map<String, Object> evt = new HashMap<>();
            evt.put("__system", "presence");
            evt.put("projectId", projectId);
            evt.put("online", online);
            evt.put("serverTs", ts);
            messaging.convertAndSend("/topic/projects/" + projectId + "/presence", evt);
        }
    }
}
