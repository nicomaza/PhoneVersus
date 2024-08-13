package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.BrandDto;
import celulares.cordobacelulares.entities.BrandEntity;
import celulares.cordobacelulares.repository.BrandJPA;
import celulares.cordobacelulares.services.BrandService;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BrandServiceImpl implements BrandService {

    private final BrandJPA brandJPA;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    public BrandServiceImpl(BrandJPA brandJPA) {
        this.brandJPA = brandJPA;
    }

    @Override
    @Transactional
    public String createBrand(BrandDto brandDTO) {
        // Verificar si el DTO es nulo
        if (brandDTO == null) {
            throw new IllegalArgumentException("Brand DTO cannot be null");
        }

        if (brandJPA.existsBrandEntitiesByBrandName(brandDTO.getBrandName())) {
            throw new IllegalArgumentException("Brand already exists");
        }

        // Verificar si el nombre de la marca es nulo o vacío
        String brandName = brandDTO.getBrandName();
        if (brandName == null || brandName.isEmpty()) {
            throw new IllegalArgumentException("Brand name cannot be null or empty");
        }

        // Crear una nueva entidad de marca y guardarla en la base de datos
        BrandEntity newBrand = new BrandEntity();
        newBrand.setBrandName(brandName);
        BrandEntity savedBrand = brandJPA.save(newBrand);

        // Verificar si la marca guardada es nula (esto debería ser poco probable)
        if (savedBrand == null || savedBrand.getBrandName() == null) {
            throw new IllegalStateException("Saved brand entity is null or has a null name");
        }

        // Devolver el nombre de la marca guardada
        return savedBrand.getBrandName();
    }
    @Override
    public List<BrandDto> getAllBrands() {
        // Obtener todas las marcas
        List<BrandEntity> brandEntities = brandJPA.findAll();

        // Convertir las entidades a DTOs
        return brandEntities.stream()
                .map(brandEntity -> modelMapper.map(brandEntity, BrandDto.class))
                .collect(Collectors.toList());
    }
    public BrandEntity getBrandById(Long id) {
        return brandJPA.findById(id).orElse(null);
    }

    public BrandEntity saveBrand(BrandEntity brand) {
        return brandJPA.save(brand);
    }

    public void deleteBrand(Long id) {
        brandJPA.deleteById(id);
    }

}