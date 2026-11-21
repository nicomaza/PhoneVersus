package celulares.cordobacelulares.security;

import celulares.cordobacelulares.config.BombaiSecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.UUID;

@Configuration
public class AdminSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/celulares/**", "/seo-assets/**").permitAll()
                        .requestMatchers("/api/admin/**", "/api/config/bombai/**").hasRole("BOMBAI_ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUsers(BombaiSecurityProperties properties, PasswordEncoder passwordEncoder) {
        boolean configured = properties.isConfigured();
        String username = configured ? properties.getUsername().trim() : "__disabled_bombai_admin__";
        String password = configured ? properties.getPassword() : UUID.randomUUID().toString() + UUID.randomUUID();
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("BOMBAI_ADMIN")
                        .build()
        );
    }
}
