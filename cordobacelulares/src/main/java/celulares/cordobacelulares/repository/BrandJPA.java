package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.BrandEntity;
import celulares.cordobacelulares.entities.PhoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BrandJPA extends JpaRepository<BrandEntity,Long> {
    boolean existsBrandEntitiesByBrandName(String name);

}
