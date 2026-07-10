package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialCreateRequest;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialPatchRequest;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialResponse;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialUpdateRequest;

import java.util.List;

public interface TiendaPorteCredentialService {

    TiendaPorteCredentialResponse create(TiendaPorteCredentialCreateRequest request);

    List<TiendaPorteCredentialResponse> getAll();

    TiendaPorteCredentialResponse getById(Long id);

    TiendaPorteCredentialResponse getActive();

    TiendaPorteCredentialResponse update(Long id, TiendaPorteCredentialUpdateRequest request);

    TiendaPorteCredentialResponse patch(Long id, TiendaPorteCredentialPatchRequest request);

    void delete(Long id);
}
