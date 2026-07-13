package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.BlockedCatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BlockedCatalogProductRepository extends JpaRepository<BlockedCatalogProduct, Long> {

    Optional<BlockedCatalogProduct> findByOrigenAndExternalProductId(String origen, Long externalProductId);

    List<BlockedCatalogProduct> findAllByOrigenOrderByBlockedAtDescIdDesc(String origen);

    void deleteByOrigenAndExternalProductId(String origen, Long externalProductId);

    @Query("select product.externalProductId from BlockedCatalogProduct product where product.origen = :origen")
    Set<Long> findExternalProductIdsByOrigen(@Param("origen") String origen);

    @Query("select product.normalizedModelKey from BlockedCatalogProduct product where product.origen = :origen")
    Set<String> findNormalizedModelKeysByOrigen(@Param("origen") String origen);
}
