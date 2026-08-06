package celulares.cordobacelulares.integration.liberadosya.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LiberadosYaProduct {

    private String nombre;
    private String slug;
    private String url;
    private String urlDolares;
    private String marca;
    private String modelo;
    private String versionComercial;
    private LiberadosYaPrices precios;
    private String disponibilidad;
    private Boolean tieneStock;
    private List<String> colores;
    private List<String> variantes;
    private String descripcionTexto;
    private String tituloFichaTecnica;
    private Map<String, Object> fichaResumida;
    private List<String> ventajas;
    private Map<String, Map<String, Object>> especificaciones;
    private String imagenPrincipal;
    private List<String> imagenes;
    private Map<String, Object> validacion;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant extraidoEn;
}
