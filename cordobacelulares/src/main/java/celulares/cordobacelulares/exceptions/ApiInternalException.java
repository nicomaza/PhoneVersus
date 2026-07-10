package celulares.cordobacelulares.exceptions;

public class ApiInternalException extends RuntimeException {

    public ApiInternalException(String message) {
        super(message);
    }

    public ApiInternalException(String message, Throwable cause) {
        super(message, cause);
    }
}
