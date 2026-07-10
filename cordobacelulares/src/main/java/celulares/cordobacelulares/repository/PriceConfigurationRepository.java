package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.PriceConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceConfigurationRepository extends JpaRepository<PriceConfiguration, Long> {
}
