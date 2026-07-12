package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.model.ModelNewDto;
import celulares.cordobacelulares.entities.BrandEntity;
import celulares.cordobacelulares.entities.ModelEntity;
import celulares.cordobacelulares.repository.ModelJPA;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelServiceImplPolicyTest {

    @Test
    void adminModelListUsesProductPolicy() {
        ModelServiceImpl service = new ModelServiceImpl();
        service.modelRepository = mock(ModelJPA.class);
        service.catalogProductPolicy = new CatalogProductPolicy(new CatalogCategoryResolver());

        when(service.modelRepository.findAll()).thenReturn(List.of(
                model(1L, "SAMSUNG A56 5G", "SAMSUNG"),
                model(2L, "TABLET SAMSUNG X620 TAB S10 FE", "SAMSUNG"),
                model(3L, "BATERIA IPHONE 6 ORIGINAL", "IPHONE")
        ));

        List<ModelNewDto> models = service.getAllModelsDto();
        Map<String, ModelNewDto> byName = models.stream()
                .collect(Collectors.toMap(ModelNewDto::getModel, Function.identity()));

        assertThat(byName).containsOnlyKeys("SAMSUNG A56 5G", "TABLET SAMSUNG X620 TAB S10 FE");
        assertThat(byName.get("SAMSUNG A56 5G").getBrand()).isEqualTo("SAMSUNG");
        assertThat(byName.get("TABLET SAMSUNG X620 TAB S10 FE").getBrand()).isEqualTo("ARTICULOS VARIOS");
    }

    private ModelEntity model(Long id, String modelName, String brandName) {
        BrandEntity brand = new BrandEntity();
        brand.setIdBrand(id);
        brand.setBrandName(brandName);

        ModelEntity model = new ModelEntity();
        model.setIdModel(id);
        model.setModelName(modelName);
        model.setBrand(brand);
        return model;
    }
}
