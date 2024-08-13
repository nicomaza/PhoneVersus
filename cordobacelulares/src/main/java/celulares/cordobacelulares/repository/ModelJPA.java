package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.ModelEntity;
import celulares.cordobacelulares.entities.PhoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelJPA extends JpaRepository<ModelEntity,Long> {
    Optional<ModelEntity> findByModelName(String modelName);

    @Query("SELECT m FROM ModelEntity m WHERE LOWER(m.modelName) = LOWER(:modelName)")
    Optional<ModelEntity> findByModelNameIgnoreCase(@Param("modelName") String modelName);
}

