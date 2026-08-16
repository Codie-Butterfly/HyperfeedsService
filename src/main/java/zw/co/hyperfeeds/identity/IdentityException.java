package zw.co.hyperfeeds.identity;

import org.springframework.http.HttpStatus;

class IdentityException extends RuntimeException {
    final HttpStatus status;
    final String code;
    IdentityException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
}
