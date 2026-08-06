package celulares.cordobacelulares.integration.liberadosya.exception;

public class LiberadosYaCatalogNotAvailableException extends LiberadosYaException {

    public LiberadosYaCatalogNotAvailableException() {
        super(
                "LIBERADOSYA_CATALOG_NOT_AVAILABLE",
                "El catalogo externo todavia no esta disponible"
        );
    }
}
