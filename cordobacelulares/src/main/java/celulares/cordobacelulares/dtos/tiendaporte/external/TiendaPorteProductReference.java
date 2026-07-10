package celulares.cordobacelulares.dtos.tiendaporte.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiendaPorteProductReference {

    private Long id;
    private String name;
    private String priceUsd;
    private Map<String, Object> colorPrices;

    @JsonAlias({"precioPesos", "pricePesos", "precioArs", "precioARS", "priceArs", "priceARS"})
    private String pricePesos;

    @JsonProperty("Category")
    private TiendaPorteCategory category;
}
