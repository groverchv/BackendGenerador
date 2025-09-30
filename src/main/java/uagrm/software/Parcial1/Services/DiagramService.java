package uagrm.software.Parcial1.Services;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uagrm.software.Parcial1.Models.DiagramEntity;
import uagrm.software.Parcial1.Repository.DiagramRepository;

@Service
@RequiredArgsConstructor
public class DiagramService {
    
    private final DiagramRepository diagramRepository;

    public DiagramEntity buscarPorId(Long id) {
        return diagramRepository.findById(id).orElse(null);
    }
}
