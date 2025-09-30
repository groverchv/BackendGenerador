package uagrm.software.Parcial1.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uagrm.software.Parcial1.Models.DiagramEntity;

@Repository
public interface DiagramRepository extends JpaRepository<DiagramEntity, Long> {

    // Buscar el diagrama de un proyecto en específico
    List<DiagramEntity> findByProject_Id(Long projectId);
}
