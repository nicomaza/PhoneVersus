package celulares.cordobacelulares.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

public class JwtService {

    private final String SECRET_KEY = "miClaveSuperSecreta"; // Cambia esto por una clave segura

    public String generateToken(String username) {
        long expirationTime = 1000 * 60 * 60; // 1 hora

        return Jwts.builder()
                .setSubject(username) // Establece el usuario
                .setIssuedAt(new Date()) // Fecha de creación
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime)) // Fecha de expiración
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY) // Firma con la clave secreta
                .compact();
    }
}
