package celulares.cordobacelulares.utils;

import org.slf4j.MDC;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

public final class TiendaPorteDiagnostics {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final int DEFAULT_BODY_PREVIEW_LENGTH = 500;

    private TiendaPorteDiagnostics() {
    }

    public static String currentCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        return correlationId == null || correlationId.isBlank() ? "-" : correlationId;
    }

    public static String newCorrelationId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String sanitizeCorrelationId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "");
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() > 64 ? sanitized.substring(0, 64) : sanitized;
    }

    public static Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public static String rootCauseLabel(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        if (rootCause == null) {
            return "-";
        }
        String message = rootCause.getMessage();
        return rootCause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public static String safeUriHostAndPath(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            return "-";
        }
        try {
            return safeUriHostAndPath(URI.create(rawUri.trim()));
        } catch (IllegalArgumentException ex) {
            return "invalid-uri";
        }
    }

    public static String safeUriHostAndPath(URI uri) {
        if (uri == null) {
            return "-";
        }
        StringBuilder value = new StringBuilder();
        if (uri.getScheme() != null) {
            value.append(uri.getScheme()).append("://");
        }
        if (uri.getHost() != null) {
            value.append(uri.getHost());
        } else {
            value.append("unknown-host");
        }
        if (uri.getPath() != null && !uri.getPath().isBlank()) {
            value.append(uri.getPath());
        }
        return value.toString();
    }

    public static String safeBodyPreview(String body) {
        return safeBodyPreview(body, DEFAULT_BODY_PREVIEW_LENGTH);
    }

    public static String safeBodyPreview(String body, int maxLength) {
        if (body == null || body.isBlank()) {
            return "";
        }
        int safeMaxLength = Math.max(1, maxLength);
        String sanitized = body
                .replaceAll("(?i)(access[_-]?token|csrf[_-]?token|password|authorization|cookie)\"?\\s*[:=]\\s*\"?[^,\"}\\s]+", "$1=<redacted>")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return sanitized.length() <= safeMaxLength ? sanitized : sanitized.substring(0, safeMaxLength);
    }

    public static int bodySize(String body) {
        return body == null ? 0 : body.length();
    }

    public static String maskUsername(String username) {
        if (username == null || username.isBlank()) {
            return "-";
        }
        String trimmed = username.trim();
        int atIndex = trimmed.indexOf('@');
        String local = atIndex >= 0 ? trimmed.substring(0, atIndex) : trimmed;
        String domain = atIndex >= 0 ? trimmed.substring(atIndex) : "";
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2).toLowerCase(Locale.ROOT) + "***" + domain;
    }
}
