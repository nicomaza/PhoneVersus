package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.TiendaPorteCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TiendaPorteCredentialRepository extends JpaRepository<TiendaPorteCredential, Long> {

    Optional<TiendaPorteCredential> findFirstByActivaTrueOrderByUpdatedAtDescIdDesc();

    @Modifying
    @Query("update TiendaPorteCredential c set c.activa = false where c.id <> :activeId and c.activa = true")
    void deactivateOtherActiveCredentials(@Param("activeId") Long activeId);
}
