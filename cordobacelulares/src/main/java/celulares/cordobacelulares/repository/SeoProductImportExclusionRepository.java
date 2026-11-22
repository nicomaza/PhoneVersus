package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.SeoProductImportExclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeoProductImportExclusionRepository extends JpaRepository<SeoProductImportExclusion, Long> {
}
