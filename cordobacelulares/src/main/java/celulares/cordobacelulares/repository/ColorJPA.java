package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.BrandEntity;
import celulares.cordobacelulares.entities.ColorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface ColorJPA extends JpaRepository<ColorEntity,Long> {
}
