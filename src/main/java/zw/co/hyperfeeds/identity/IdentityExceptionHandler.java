package zw.co.hyperfeeds.identity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
class IdentityExceptionHandler {
    @ExceptionHandler(IdentityException.class)
    ResponseEntity<?> identity(IdentityException ex) {
        return ResponseEntity.status(ex.status).body(Map.of(
                "code", ex.code, "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getField(), e -> e.getDefaultMessage(), (a, b) -> a));
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "errors", errors,
                "timestamp", Instant.now().toString()));
    }
}
