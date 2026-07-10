package celulares.cordobacelulares.dtos.tiendaporte.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TiendaPorteCategoryPageResponse {

    private String categoria;
    private int page;
    private int limit;
    private int total;
    private int totalPages;
    private boolean hasNext;
    private List<TiendaPorteBrandResponse> data;
}
