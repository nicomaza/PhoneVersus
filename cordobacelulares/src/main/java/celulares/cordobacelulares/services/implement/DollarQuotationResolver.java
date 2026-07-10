package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DollarQuotationResolver {

    public BigDecimal resolve(String headerValue, BigDecimal queryValue, PriceConfiguration configuration) {
        BigDecimal headerQuotation = parseHeader(headerValue);
        if (isProvided(headerQuotation)) {
            return headerQuotation;
        }
        if (queryValue != null && queryValue.signum() < 0) {
            throw new TiendaPorteBadRequestException("cotizacionDolar no puede ser negativa");
        }
        if (isProvided(queryValue)) {
            return queryValue;
        }
        return configuration == null ? null : configuration.getDolarBillete();
    }

    private BigDecimal parseHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(headerValue.trim());
            if (value.signum() < 0) {
                throw new TiendaPorteBadRequestException("X-Cotizacion-Dolar no puede ser negativa");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new TiendaPorteBadRequestException("X-Cotizacion-Dolar debe ser numerico");
        }
    }

    private boolean isProvided(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
