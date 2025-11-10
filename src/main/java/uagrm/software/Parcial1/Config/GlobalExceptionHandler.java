package uagrm.software.Parcial1.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para API REST
 * Captura errores y los convierte en respuestas JSON estructuradas
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Respuesta de error estructurada
     */
    private Map<String, Object> buildErrorResponse(
            HttpStatus status, 
            String message, 
            String path,
            Exception ex
    ) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", message);
        error.put("path", path);
        
        // Log del error
        logger.error("API Error [{}]: {} - Path: {}", 
            status.value(), message, path, ex);
        
        return error;
    }

    /**
     * Maneja excepciones de recursos no encontrados
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(
            ResourceNotFoundException ex, 
            WebRequest request
    ) {
        Map<String, Object> error = buildErrorResponse(
            HttpStatus.NOT_FOUND,
            ex.getMessage(),
            request.getDescription(false).replace("uri=", ""),
            ex
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Maneja argumentos inválidos (validación)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, 
            WebRequest request
    ) {
        Map<String, Object> error = buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            ex.getMessage(),
            request.getDescription(false).replace("uri=", ""),
            ex
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja todas las demás excepciones no capturadas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(
            Exception ex, 
            WebRequest request
    ) {
        Map<String, Object> error = buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Error interno del servidor. Por favor contacte al administrador.",
            request.getDescription(false).replace("uri=", ""),
            ex
        );
        
        // Log completo del stack trace solo para errores 500
        logger.error("Unhandled exception:", ex);
        
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

/**
 * Excepción personalizada para recursos no encontrados
 */
class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
