package celulares.cordobacelulares.dtos.tiendaporte.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCatalogPageResponse {

    private int page;
    private int limit;
    private int total;
    private int totalPages;
    private boolean hasNext;
    private List<AdminCatalogProductRowResponse> data;
}
