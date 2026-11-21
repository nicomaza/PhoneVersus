package celulares.cordobacelulares.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bombai.security")
public class BombaiSecurityProperties {

    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isConfigured() {
        return username != null && !username.isBlank() && password != null && password.length() >= 12;
    }
}
