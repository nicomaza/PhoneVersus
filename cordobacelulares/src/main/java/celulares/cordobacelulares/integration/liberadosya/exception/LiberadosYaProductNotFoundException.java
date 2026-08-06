package celulares.cordobacelulares.integration.liberadosya.exception;

public class LiberadosYaProductNotFoundException extends LiberadosYaException {

    public LiberadosYaProductNotFoundException() {
        super(
                "LIBERADOSYA_PRODUCT_NOT_FOUND",
                "No se encontro una coincidencia confiable para el producto solicitado"
        );
    }
}
