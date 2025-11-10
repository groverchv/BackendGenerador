// src/main/java/uagrm/software/Parcial1/Config/WebSocketConfig.java
package uagrm.software.Parcial1.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Configuración profesional de WebSocket con SockJS
 * 
 * Características:
 * - Heartbeat automático cada 10 segundos
 * - Reconexión automática del cliente
 * - Buffer de 5MB para mensajes grandes
 * - Pool de threads escalable (10-50)
 * - Timeouts configurados para prevenir bloqueos
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    logger.info("🔌 Registrando endpoint WebSocket en /ws con SockJS");
    
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // Permitir todos los orígenes (ajustar en producción)
            .withSockJS()
            .setSessionCookieNeeded(false)
            .setHeartbeatTime(25000)       // Heartbeat cada 25 segundos
            .setDisconnectDelay(5000);     // Esperar 5s antes de desconectar
    
    logger.info("✅ WebSocket configurado exitosamente");
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    logger.info("📡 Configurando message broker con heartbeat");
    
    // Scheduler para heartbeats
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(10);
    scheduler.setThreadNamePrefix("ws-heartbeat-");
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.initialize();
    
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue")
            .setTaskScheduler(scheduler)
            .setHeartbeatValue(new long[]{10000, 10000}); // Heartbeat bidireccional cada 10s
    
    logger.info("✅ Message broker configurado con heartbeat activo");
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
