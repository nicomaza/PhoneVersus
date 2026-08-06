package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaResponse {

    private BombaiComparativaSummaryResponse summary;
    private List<BombaiComparativaProductResponse> products;
}
