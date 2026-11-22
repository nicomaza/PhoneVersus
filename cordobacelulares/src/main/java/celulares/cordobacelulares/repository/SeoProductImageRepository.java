package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.SeoProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface SeoProductImageRepository extends JpaRepository<SeoProductImage, Long> {

    Optional<SeoProductImage> findByIdAndContentId(Long id, Long contentId);

    List<SeoProductImage> findAllByContentIdAndImportedFromPorteTrue(Long contentId);
}
