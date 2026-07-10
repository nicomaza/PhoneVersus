package celulares.cordobacelulares.exceptions;

public class TiendaPorteAuthenticationException extends RuntimeException {

    public TiendaPorteAuthenticationException(String message) {
        super(message);
    }

    public TiendaPorteAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
