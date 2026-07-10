package celulares.cordobacelulares.dtos.tiendaporte.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiendaPorteSessionUser {

    private String accessToken;
}
