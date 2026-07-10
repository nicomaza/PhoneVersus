package celulares.cordobacelulares.dtos.tiendaporte.credentials;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TiendaPorteCredentialResponse {

    private Long id;
    private String username;
    private Boolean activa;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
