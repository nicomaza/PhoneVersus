package celulares.cordobacelulares.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tiendaporte")
public class TiendaPorteProperties {

    private String authBaseUrl = "https://sistema.tiendaporte.com";
    private String apiBaseUrl = "https://api.tiendaporte.com";
    private int productsLimit = 5000;
    private int connectTimeoutSeconds = 10;
    private int requestTimeoutSeconds = 30;

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public int getProductsLimit() {
        return productsLimit;
    }

    public void setProductsLimit(int productsLimit) {
        this.productsLimit = productsLimit;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

}
