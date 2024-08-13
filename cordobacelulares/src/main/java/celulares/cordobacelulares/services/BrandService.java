package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.BrandDto;
import celulares.cordobacelulares.entities.BrandEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface BrandService {


    String createBrand(BrandDto brandDTO);

    List<BrandDto> getAllBrands() ;

     BrandEntity getBrandById(Long id) ;
     BrandEntity saveBrand(BrandEntity brand) ;
     void deleteBrand(Long id) ;

}
