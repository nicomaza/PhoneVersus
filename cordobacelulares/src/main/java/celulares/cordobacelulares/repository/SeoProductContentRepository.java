package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.SeoProductContent;
import celulares.cordobacelulares.entities.SeoProductContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeoProductContentRepository extends JpaRepository<SeoProductContent, Long> {

    Optional<SeoProductContent> findBySeoCatalogProductSlug(String slug);

    Optional<SeoProductContent> findBySeoCatalogProductId(Long seoCatalogProductId);

    List<SeoProductContent> findAllByStatus(SeoProductContentStatus status);

    @Query("select c.seoCatalogProduct.slug from SeoProductContent c "
            + "where c.status = :status and c.publishedSnapshot is not null")
    List<String> findSlugsWithPublishedSnapshot(@Param("status") SeoProductContentStatus status);
}
