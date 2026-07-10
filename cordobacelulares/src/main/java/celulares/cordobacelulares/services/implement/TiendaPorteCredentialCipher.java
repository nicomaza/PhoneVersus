package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.exceptions.ApiInternalException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class TiendaPorteCredentialCipher {

    private static final String ENCRYPTED_PREFIX = "ENC:";
    private static final byte[] INTERNAL_AES_KEY = "PhoneVersusTiendaPorteKey2026!!!".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainPassword) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream payload = new ByteArrayOutputStream(iv.length + encrypted.length);
            payload.writeBytes(iv);
            payload.writeBytes(encrypted);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload.toByteArray());
        } catch (GeneralSecurityException ex) {
            throw new ApiInternalException("No se pudo cifrar la credencial de Tienda Porte", ex);
        }
    }

    public String decrypt(String storedPassword) {
        if (storedPassword == null || !storedPassword.startsWith(ENCRYPTED_PREFIX)) {
            return storedPassword;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(storedPassword.substring(ENCRYPTED_PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new ApiInternalException("No se pudo descifrar la credencial de Tienda Porte");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ApiInternalException("No se pudo descifrar la credencial de Tienda Porte", ex);
        } catch (GeneralSecurityException ex) {
            throw new ApiInternalException("No se pudo descifrar la credencial de Tienda Porte", ex);
        }
    }

    private SecretKeySpec secretKey() {
        return new SecretKeySpec(INTERNAL_AES_KEY, "AES");
    }
}
