package celulares.cordobacelulares.integration.liberadosya.exception;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "celulares.cordobacelulares.integration.liberadosya")
public class LiberadosYaExceptionHandler {

    @ExceptionHandler(LiberadosYaProductNotFoundException.class)
    public ResponseEntity<LiberadosYaErrorResponse> handleNotFound(LiberadosYaProductNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(LiberadosYaMatchAmbiguousException.class)
    public ResponseEntity<LiberadosYaErrorResponse> handleAmbiguous(LiberadosYaMatchAmbiguousException ex) {
        return error(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(LiberadosYaCatalogNotAvailableException.class)
    public ResponseEntity<LiberadosYaErrorResponse> handleNotAvailable(LiberadosYaCatalogNotAvailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex);
    }

    @ExceptionHandler(LiberadosYaBadRequestException.class)
    public ResponseEntity<LiberadosYaErrorResponse> handleBadRequest(LiberadosYaBadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    private ResponseEntity<LiberadosYaErrorResponse> error(HttpStatus status, LiberadosYaException ex) {
        return ResponseEntity.status(status).body(new LiberadosYaErrorResponse(ex.getCode(), ex.getMessage()));
    }
}
