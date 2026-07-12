package celulares.cordobacelulares.dtos.tiendaporte.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiendaPorteExternalProduct {

    private String name;

    @JsonAlias({"precioPesos", "pricePesos", "precioArs", "precioARS", "priceArs", "priceARS"})
    private String pricePesos;

    private Map<String, Integer> colorStock;
    private TiendaPorteProductReference productReference;

    @JsonProperty("Category")
    private TiendaPorteCategory category;

    @JsonIgnore
    private String sourceCategory;

    @JsonIgnore
    private String originalCategory;
}
