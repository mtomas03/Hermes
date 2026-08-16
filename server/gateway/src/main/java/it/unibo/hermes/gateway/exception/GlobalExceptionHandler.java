package it.unibo.hermes.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Translates domain and validation exceptions into RFC-7807 {@link ProblemDetail} responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles request body validation failures.
     *
     * @param ex the exception thrown when request argument validation fails
     * @return a problem detail response with status 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setDetail(detail);
        return pd;
    }

    /**
     * Handles authentication failures resulting from invalid user credentials.
     *
     * @param ex the bad credentials exception
     * @return a problem detail response with status 401 Unauthorized
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("Authentication failed");
        pd.setDetail("Invalid username or password");
        return pd;
    }

    /**
     * Handles invalid argument errors.
     *
     * @param ex the illegal argument exception
     * @return a problem detail response with status 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Bad request");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /**
     * Handles persistence failures by logging the error and prompting the client to retry.
     *
     * @param ex the persistence unavailable exception
     * @return a problem detail response with status 503 Service Unavailable
     */
    @ExceptionHandler(PersistenceUnavailableException.class)
    public ProblemDetail handlePersistenceUnavailable(PersistenceUnavailableException ex) {
        log.error("Persistence unavailable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        pd.setTitle("Service temporarily unavailable");
        pd.setDetail("Message could not be persisted. Please retry.");
        return pd;
    }

    /**
     * Handles generic domain exceptions.
     *
     * @param ex the root application exception
     * @return a problem detail response with status 500 Internal Server Error
     */
    @ExceptionHandler(HermesException.class)
    public ProblemDetail handleHermes(HermesException ex) {
        log.error("Internal error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal error");
        pd.setDetail(ex.getMessage());
        return pd;
    }
}