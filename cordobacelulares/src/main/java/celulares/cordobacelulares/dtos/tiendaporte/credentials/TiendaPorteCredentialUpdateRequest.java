package celulares.cordobacelulares.dtos.tiendaporte.credentials;

import lombok.Data;

@Data
public class TiendaPorteCredentialUpdateRequest {

    private String username;
    private String password;
    private Boolean activa;
}
