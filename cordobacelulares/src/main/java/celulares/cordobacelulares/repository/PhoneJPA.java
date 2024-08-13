package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.PhoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhoneJPA extends JpaRepository<PhoneEntity,Long> {

    // Consulta para obtener todos los teléfonos de un modelo específico
    @Query("SELECT p FROM PhoneEntity p WHERE p.model = :model")
    List<PhoneEntity> findAllByModel(@Param("model") String model);

    // Consulta para obtener todos los teléfonos de una marca específica
    @Query("SELECT p FROM PhoneEntity p WHERE p.model.brand.brandName = :brandName")
    List<PhoneEntity> findPhonesByBrandName(@Param("brandName") String brandName);


    // Consulta para obtener todos los teléfonos de un modelo y una marca específica
    @Query("SELECT p FROM PhoneEntity p WHERE p.model.brand = :brand AND p.model = :model")
    List<PhoneEntity> findAllByBrandAndModel(@Param("brand") String brand, @Param("model") String model);
}
