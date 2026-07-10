package celulares.cordobacelulares.dtos.tiendaporte.credentials;

import lombok.Data;

@Data
public class TiendaPorteCredentialCreateRequest {

    private String username;
    private String password;
    private Boolean activa;
}
