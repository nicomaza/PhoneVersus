package celulares.cordobacelulares.dtos.tiendaporte.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TiendaPorteBrandResponse {

    private String marca;
    private List<TiendaPorteModelResponse> modelos;
}
