package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.BoxContentsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoxContentJPA extends JpaRepository<BoxContentsEntity,Long> {
}
