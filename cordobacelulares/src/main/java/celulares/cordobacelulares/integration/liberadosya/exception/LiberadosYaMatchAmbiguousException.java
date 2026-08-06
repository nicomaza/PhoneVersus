package celulares.cordobacelulares.integration.liberadosya.exception;

public class LiberadosYaMatchAmbiguousException extends LiberadosYaException {

    public LiberadosYaMatchAmbiguousException() {
        super(
                "LIBERADOSYA_PRODUCT_MATCH_AMBIGUOUS",
                "Se encontraron multiples coincidencias posibles para el producto solicitado"
        );
    }
}
