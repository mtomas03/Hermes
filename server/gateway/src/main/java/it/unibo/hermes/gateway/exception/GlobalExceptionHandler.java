package it.unibo.hermes.gateway.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
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
     * Handles authorisation failures raised by the application layer.
     *
     * @param ex the access denied exception
     * @return a problem detail response with status 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Access denied");
        pd.setDetail(ex.getMessage());
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
        pd.setDetail("The persistence layer is temporarily unavailable. Please retry.");
        return pd;
    }

    /**
     * Handles a violated data integrity constraint.
     *
     * @param ex the integrity violation
     * @return a problem detail response with status 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Integrity constraint violated: {}", ex.getClass().getSimpleName());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Conflict");
        pd.setDetail("The requested account already exists");
        return pd;
    }

    /**
     * Distinguishes an unavailable account database from other data-access errors.
     *
     * @param ex the data-access exception
     * @return 503 if the database is unreachable or timed out, otherwise 500
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccess(DataAccessException ex) {
        if (isInfrastructureFailure(ex)) {
            return databaseUnavailable(ex);
        }
        log.error("Data access error", ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal error");
        return pd;
    }

    /**
     * Handles failures to open a transaction.
     *
     * @param ex the transaction exception
     * @return a problem detail response with status 503 Service Unavailable
     */
    @ExceptionHandler(CannotCreateTransactionException.class)
    public ProblemDetail handleCannotCreateTransaction(CannotCreateTransactionException ex) {
        return databaseUnavailable(ex);
    }

    /**
     * Handles generic domain exceptions.
     *
     * @param ex the root application exception
     * @return a problem detail response with status 500 Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleHermes(RuntimeException ex) {
        log.error("Internal error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal error");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    private ProblemDetail databaseUnavailable(Exception ex) {
        log.error("Account database unavailable: {}", ex.getClass().getSimpleName());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        pd.setTitle("Service temporarily unavailable");
        pd.setDetail("The account database is temporarily unavailable. Please retry later.");
        return pd;
    }

    private static boolean isInfrastructureFailure(DataAccessException ex) {
        if (ex instanceof DataAccessResourceFailureException || ex instanceof TransientDataAccessException) {
            return true;
        }
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLTransientConnectionException
                    || t instanceof SQLNonTransientConnectionException
                    || t instanceof SQLRecoverableException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
