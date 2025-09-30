package uagrm.software.Parcial1.Services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import uagrm.software.Parcial1.Models.ProjectEntity;
import uagrm.software.Parcial1.Models.DiagramEntity;
import uagrm.software.Parcial1.Repository.ProjectRepository;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public List<ProjectEntity> listarProyectos() {
        return projectRepository.findAll();
    }

    public ProjectEntity buscarPorId(Long id) {
        return projectRepository.findById(id).orElse(null);
    }

    /**
     * Guarda un proyecto y crea automáticamente su diagrama asociado
     */
    @Transactional
    public ProjectEntity guardarProyecto(ProjectEntity proyecto) {
        if (proyecto.getDiagram() == null) {
            DiagramEntity diagrama = new DiagramEntity();
            diagrama.setName("Diagrama de " + proyecto.getName());
            diagrama.setNodes("[]");
            diagrama.setEdges("[]");
            proyecto.setDiagram(diagrama);
        }

        return projectRepository.save(proyecto);
    }

    /**
     * Actualiza el diagrama de un proyecto existente
     */
    @Transactional
    public DiagramEntity actualizarDiagrama(Long projectId, DiagramEntity nuevo) {
        ProjectEntity p = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));

        DiagramEntity d = p.getDiagram();
        if (d == null) {
            throw new RuntimeException("Diagrama no encontrado");
        }

        // ✅ Copiar SOLO los campos editables
        if (nuevo.getName() != null)
            d.setName(nuevo.getName());
        if (nuevo.getNodes() != null)
            d.setNodes(nuevo.getNodes());
        if (nuevo.getEdges() != null)
            d.setEdges(nuevo.getEdges());
        if (nuevo.getViewport() != null)
            d.setViewport(nuevo.getViewport());

        // Guardar en cascada (ya que diagram pertenece al proyecto)
        projectRepository.save(p);

        return d;
    }

    public void eliminarProyecto(Long id) {
        projectRepository.deleteById(id);
    }
}
