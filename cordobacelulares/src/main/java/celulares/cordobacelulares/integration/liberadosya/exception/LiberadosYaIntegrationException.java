package celulares.cordobacelulares.integration.liberadosya.exception;

public class LiberadosYaIntegrationException extends LiberadosYaException {

    public LiberadosYaIntegrationException(String message) {
        super("LIBERADOSYA_INTEGRATION_ERROR", message);
    }

    public LiberadosYaIntegrationException(String message, Throwable cause) {
        super("LIBERADOSYA_INTEGRATION_ERROR", message, cause);
    }
}
