package celulares.cordobacelulares.dtos.tiendaporte.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TiendaPorteColorStockResponse {

    private String color;
    private Integer stock;
}
