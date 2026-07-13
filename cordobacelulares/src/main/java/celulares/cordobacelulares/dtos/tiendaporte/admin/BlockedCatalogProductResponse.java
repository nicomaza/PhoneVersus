package celulares.cordobacelulares.dtos.tiendaporte.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockedCatalogProductResponse {

    private Long id;
    private String marca;
    private String modelo;
    private BigDecimal precioUsd;
    private String origen;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant blockedAt;
}
