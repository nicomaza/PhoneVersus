package celulares.cordobacelulares.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "supplier-sheet")
public class SupplierSheetProperties {

    private boolean enabled = true;
    private String csvUrl = "https://docs.google.com/spreadsheets/d/e/2PACX-1vQq-A-ZD2CU9qt9hd8DCbMjfY8yi503PrF8hiAhyyanaJcVDSxu2pPUWj73v-Imhym9aYGs66bPamIB/pub?gid=0&single=true&output=csv";
    private int cacheMinutes = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCsvUrl() {
        return csvUrl;
    }

    public void setCsvUrl(String csvUrl) {
        this.csvUrl = csvUrl;
    }

    public int getCacheMinutes() {
        return cacheMinutes;
    }

    public void setCacheMinutes(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }
}
