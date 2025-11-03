// src/main/java/uagrm/software/Parcial1/Config/WebSocketConfig.java
package uagrm.software.Parcial1.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // cualquiera
            .withSockJS()                  // un solo endpoint (nativo + fallback)
            .setSessionCookieNeeded(false) // no requiere cookies
            .setHeartbeatTime(25000)       // heartbeat cada 25 segundos
            .setDisconnectDelay(5000);     // esperar 5 segundos antes de desconectar
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(10);
    scheduler.setThreadNamePrefix("ws-heartbeat-");
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.initialize();
    
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue")
            .setTaskScheduler(scheduler)
            .setHeartbeatValue(new long[]{10000, 10000}); // heartbeat cada 10 segundos
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration r) {
    r.setMessageSizeLimit(2 * 1024 * 1024)      // 2MB max mensaje
     .setSendBufferSizeLimit(5 * 1024 * 1024)   // 5MB buffer de envío
     .setSendTimeLimit(30_000)                   // 30 segundos timeout
     .setTimeToFirstMessage(30_000);             // 30 segundos para primer mensaje
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.taskExecutor()
            .corePoolSize(10)
            .maxPoolSize(50)
            .queueCapacity(1000);
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration.taskExecutor()
            .corePoolSize(10)
            .maxPoolSize(50)
            .queueCapacity(1000);
  }

  /**
   * Configuración adicional del contenedor WebSocket
   */
  @Bean
  public ServletServerContainerFactoryBean createWebSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(2 * 1024 * 1024);  // 2MB
    container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024); // 2MB
    container.setMaxSessionIdleTimeout(300000L);              // 5 minutos idle
    container.setAsyncSendTimeout(30000L);                    // 30 segundos async
    return container;
  }
}
