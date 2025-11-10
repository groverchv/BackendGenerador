package uagrm.software.Parcial1.Controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint de health check para monitoreo
 * Útil para balanceadores de carga, Kubernetes, Railway, etc.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * Health check básico
     * GET /health
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("uptime", java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds() + "s");
        health.put("service", "Generador de Diagramas UML");
        health.put("version", "1.0.0");
        
        return ResponseEntity.ok(health);
    }

    /**
     * Readiness check (para Kubernetes)
     * GET /health/ready
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "READY");
        return ResponseEntity.ok(status);
    }

    /**
     * Liveness check (para Kubernetes)
     * GET /health/live
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ALIVE");
        return ResponseEntity.ok(status);
    }
}
