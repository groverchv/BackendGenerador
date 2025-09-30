// src/main/java/uagrm/software/Parcial1/Config/WebSocketConfig.java
package uagrm.software.Parcial1.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // cualquiera
            .withSockJS();                  // un solo endpoint (nativo + fallback)
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.initialize();
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue")
            .setTaskScheduler(scheduler)
            .setHeartbeatValue(new long[]{10000, 10000});
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration r) {
    r.setMessageSizeLimit(1024 * 1024)
     .setSendBufferSizeLimit(3 * 1024 * 1024)
     .setSendTimeLimit(20_000);
  }
}
