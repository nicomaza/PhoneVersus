package celulares.cordobacelulares.config;

import celulares.cordobacelulares.dtos.common.ErrorApi;
import celulares.cordobacelulares.exceptions.ApiConflictException;
import celulares.cordobacelulares.exceptions.ApiInternalException;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.exceptions.TiendaPorteAuthenticationException;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.exceptions.TiendaPorteTimeoutException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ErrorApi errorApi = new ErrorApi(
                LocalDateTime.now().toString(), // Asegúrate de que el timestamp tenga el formato correcto
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getBindingResult().getFieldError().getDefaultMessage() // Mensaje de error específico
        );
        return new ResponseEntity<>(errorApi, HttpStatus.BAD_REQUEST);
    }

    // Aquí puedes agregar manejadores para otros tipos de excepciones si es necesario

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorApi> handleUserNotValidException(DataIntegrityViolationException ex) {
        ErrorApi errorApi = new ErrorApi(
                LocalDateTime.now().toString(), // Asegúrate de que el timestamp tenga el formato correcto
                HttpStatus.BAD_REQUEST.value(),
                "User Not Valid",
                ex.getMessage() // Mensaje de error específico para UserNotValidException
        );
        return new ResponseEntity<>(errorApi, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(DuplicateModelException.class)
    public ResponseEntity<ErrorApi> handleDuplicateModelException(DuplicateModelException ex) {
        ErrorApi errorApi = new ErrorApi(
                LocalDateTime.now().toString(), // Asegúrate de que el timestamp tenga el formato correcto
                HttpStatus.CONFLICT.value(),
                "Duplicate Model",
                ex.getMessage() // Mensaje de error específico para DuplicateModelException
        );
        return new ResponseEntity<>(errorApi, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(TiendaPorteBadRequestException.class)
    public ResponseEntity<ErrorApi> handleTiendaPorteBadRequestException(TiendaPorteBadRequestException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorApi> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "Bad Request", "El parametro '" + ex.getParameterName() + "' es obligatorio");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorApi> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "Bad Request", "Parametro invalido");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorApi> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "Bad Request", "Datos invalidos");
    }

    @ExceptionHandler(ApiNotFoundException.class)
    public ResponseEntity<ErrorApi> handleApiNotFoundException(ApiNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(ApiConflictException.class)
    public ResponseEntity<ErrorApi> handleApiConflictException(ApiConflictException ex) {
        return buildError(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(ApiInternalException.class)
    public ResponseEntity<ErrorApi> handleApiInternalException(ApiInternalException ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage());
    }

    @ExceptionHandler({TiendaPorteAuthenticationException.class, TiendaPorteIntegrationException.class})
    public ResponseEntity<ErrorApi> handleTiendaPorteIntegrationException(RuntimeException ex) {
        return buildError(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage());
    }

    @ExceptionHandler(TiendaPorteTimeoutException.class)
    public ResponseEntity<ErrorApi> handleTiendaPorteTimeoutException(TiendaPorteTimeoutException ex) {
        return buildError(HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout", ex.getMessage());
    }

    private ResponseEntity<ErrorApi> buildError(HttpStatus status, String error, String message) {
        ErrorApi errorApi = new ErrorApi(
                LocalDateTime.now().toString(),
                status.value(),
                error,
                message
        );
        return new ResponseEntity<>(errorApi, status);
    }

    public static class DuplicateModelException extends RuntimeException {
        public DuplicateModelException(String message) {
            super(message);
        }
    }
}

