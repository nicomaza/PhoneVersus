package celulares.cordobacelulares.dtos.priceconfiguration;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Schema(description = "Actualizacion parcial de configuracion. transferenciaBancaria, tarjeta3Pagos, tarjeta6Pagos y tarjeta12Pagos son porcentajes de recargo, no importes fijos.")
public class PriceConfigurationPatchRequest {

    private final Map<String, JsonNode> fields = new LinkedHashMap<>();

    @JsonAnySetter
    public void setField(String name, JsonNode value) {
        fields.put(name, value);
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    public JsonNode getField(String name) {
        return fields.get(name);
    }

    public Set<String> fieldNames() {
        return fields.keySet();
    }

    public Map<String, JsonNode> getFields() {
        return Collections.unmodifiableMap(fields);
    }
}
