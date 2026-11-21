package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.HomeBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {
    List<HomeBanner> findAllByOrderBySortOrderAscIdAsc();
    List<HomeBanner> findAllByActiveTrueOrderBySortOrderAscIdAsc();
    HomeBanner findFirstByOrderBySortOrderDescIdDesc();
}
