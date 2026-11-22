package celulares.cordobacelulares.dtos.tiendaporte.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiendaPorteProductSpecsResponse {

    private String hash;
    private String title;
    private Sheet sheet;
    private String updatedAt;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sheet {
        private String title;
        private String subtitle;
        private Source source;
        private List<Highlight> highlights = new ArrayList<>();
        private List<Section> sections = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private String url;
        private String title;
        private String domain;
        private String fetchedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Highlight {
        private String key;
        private String label;
        private String value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Section {
        private String key;
        private String title;
        private List<Row> rows = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {
        private String key;
        private String label;
        private String value;
    }
}
