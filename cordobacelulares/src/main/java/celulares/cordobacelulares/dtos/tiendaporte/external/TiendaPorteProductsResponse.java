package celulares.cordobacelulares.dtos.tiendaporte.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiendaPorteProductsResponse {

    private List<TiendaPorteExternalProduct> data;
    private TiendaPorteMeta meta;
}
