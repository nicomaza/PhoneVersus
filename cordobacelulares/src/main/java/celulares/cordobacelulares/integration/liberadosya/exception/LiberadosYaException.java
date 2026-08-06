package celulares.cordobacelulares.integration.liberadosya.exception;

public abstract class LiberadosYaException extends RuntimeException {

    private final String code;

    protected LiberadosYaException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected LiberadosYaException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
