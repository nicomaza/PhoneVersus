package celulares.cordobacelulares.integration.liberadosya.scraping;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LiberadosYaSpecificationParser {

    public Map<String, Map<String, Object>> parse(Document document) {
        Map<String, Map<String, Object>> specs = new LinkedHashMap<>();
        if (document == null) {
            return specs;
        }
        for (Element table : document.select("table")) {
            parseTable(table, specs);
        }
        return specs;
    }

    private void parseTable(Element table, Map<String, Map<String, Object>> specs) {
        String currentSection = sectionFromContext(table);
        for (Element row : table.select("tr")) {
            Elements cells = row.select("th,td");
            if (cells.size() < 2) {
                continue;
            }
            if (cells.size() >= 3) {
                String section = clean(cells.get(0).text());
                String key = clean(cells.get(1).text());
                String value = clean(cells.get(2).text());
                if (!section.isBlank()) {
                    currentSection = section;
                }
                put(specs, currentSection, key, value);
            } else {
                String key = clean(cells.get(0).text());
                String value = clean(cells.get(1).text());
                put(specs, currentSection, key, value);
            }
        }
    }

    private String sectionFromContext(Element table) {
        Element previous = table.previousElementSibling();
        while (previous != null) {
            if (previous.tagName().matches("h[1-6]")) {
                return clean(previous.text()).isBlank() ? "GENERAL" : clean(previous.text()).toUpperCase(Locale.ROOT);
            }
            previous = previous.previousElementSibling();
        }
        return "GENERAL";
    }

    private void put(Map<String, Map<String, Object>> specs, String section, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        String safeSection = section == null || section.isBlank() ? "GENERAL" : section.toUpperCase(Locale.ROOT);
        Map<String, Object> sectionValues = specs.computeIfAbsent(safeSection, ignored -> new LinkedHashMap<>());
        Object previous = sectionValues.get(key);
        if (previous == null) {
            sectionValues.put(key, value.isBlank() ? null : value);
            return;
        }
        if (previous instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list);
            if (!copy.contains(value)) {
                copy.add(value);
            }
            sectionValues.put(key, copy);
            return;
        }
        if (!String.valueOf(previous).equals(value)) {
            List<Object> values = new ArrayList<>();
            values.add(previous);
            values.add(value);
            sectionValues.put(key, values);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
