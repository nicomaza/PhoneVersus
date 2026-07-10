package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteCategoryPageResponse;

import java.math.BigDecimal;
import java.util.List;

public interface TiendaPorteService {

    List<TiendaPorteBrandResponse> getAll(BigDecimal cotizacionDolar, String cotizacionDolarHeader);

    List<TiendaPorteBrandResponse> search(String texto, BigDecimal cotizacionDolar, String cotizacionDolarHeader);

    List<TiendaPorteBrandResponse> getAllowedCategories(BigDecimal cotizacionDolar, String cotizacionDolarHeader);

    TiendaPorteCategoryPageResponse getByCategory(
            String categoria,
            Integer page,
            Integer limit,
            BigDecimal cotizacionDolar,
            String cotizacionDolarHeader
    );
}
