package celulares.cordobacelulares.integration.liberadosya.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LiberadosYaCatalogError {

    private String url;
    private String slug;
    private String code;
    private String message;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt;
}
