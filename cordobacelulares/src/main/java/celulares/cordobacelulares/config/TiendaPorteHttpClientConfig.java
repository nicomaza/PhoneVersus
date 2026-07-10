package celulares.cordobacelulares.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class TiendaPorteHttpClientConfig {

    @Bean
    public CookieManager tiendaPorteCookieManager() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return cookieManager;
    }

    @Bean
    public HttpClient tiendaPorteHttpClient(TiendaPorteProperties properties, CookieManager tiendaPorteCookieManager) {
        return HttpClient.newBuilder()
                .cookieHandler(tiendaPorteCookieManager)
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
