package celulares.cordobacelulares.exceptions;

public class TiendaPorteTimeoutException extends RuntimeException {

    public TiendaPorteTimeoutException(String message) {
        super(message);
    }

    public TiendaPorteTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
