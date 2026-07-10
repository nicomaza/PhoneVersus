package celulares.cordobacelulares.dtos.tiendaporte.credentials;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TiendaPorteCredentialPatchRequest {

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
