package celulares.cordobacelulares.integration.liberadosya.exception;

public class LiberadosYaBadRequestException extends LiberadosYaException {

    public LiberadosYaBadRequestException(String message) {
        super("LIBERADOSYA_BAD_REQUEST", message);
    }
}
