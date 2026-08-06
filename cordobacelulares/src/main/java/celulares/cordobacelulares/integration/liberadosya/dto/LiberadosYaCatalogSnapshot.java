package celulares.cordobacelulares.integration.liberadosya.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LiberadosYaCatalogSnapshot {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant generatedAt;
    private int schemaVersion;
    private String source;
    private int totalUrlsFound;
    private int totalProducts;
    private int errorsCount;
    private List<LiberadosYaProduct> products;
    private List<LiberadosYaCatalogError> errors;
}
