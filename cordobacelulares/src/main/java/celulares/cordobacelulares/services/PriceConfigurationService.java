package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationCreateRequest;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationPatchRequest;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationResponse;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationUpdateRequest;
import celulares.cordobacelulares.entities.PriceConfiguration;

public interface PriceConfigurationService {

    PriceConfigurationResponse create(PriceConfigurationCreateRequest request);

    PriceConfigurationResponse get();

    PriceConfiguration getRequiredForCatalog();

    PriceConfigurationResponse update(PriceConfigurationUpdateRequest request);

    PriceConfigurationResponse patch(PriceConfigurationPatchRequest request);

    void delete();
}
