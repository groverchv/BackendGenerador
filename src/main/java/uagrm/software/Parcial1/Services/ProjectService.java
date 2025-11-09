package uagrm.software.Parcial1.Services;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import uagrm.software.Parcial1.Models.ProjectEntity;
import uagrm.software.Parcial1.Models.DiagramEntity;
import uagrm.software.Parcial1.Repository.ProjectRepository;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DiagramRecognitionService diagramRecognitionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

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

    @Transactional
    public DiagramEntity actualizarDiagramaDesdeImagen(Long projectId, MultipartFile file) {
        try {
            // 1. Reconocer el diagrama desde la imagen
            String diagramJson = diagramRecognitionService.recognizeDiagram(file);

            // 2. Parsear el JSON para extraer nodes y edges
            Map<String, Object> diagramMap = objectMapper.readValue(diagramJson, Map.class);
            String nodes = objectMapper.writeValueAsString(diagramMap.get("nodes"));
            String edges = objectMapper.writeValueAsString(diagramMap.get("edges"));

            // 3. Crear una entidad de diagrama temporal con los nuevos datos
            DiagramEntity datosNuevos = new DiagramEntity();
            datosNuevos.setNodes(nodes);
            datosNuevos.setEdges(edges);

            // 4. Actualizar el diagrama existente
            DiagramEntity diagramaActualizado = actualizarDiagrama(projectId, datosNuevos);

            // 5. Notificar a los clientes por WebSocket
            messagingTemplate.convertAndSend("/topic/projects/" + projectId, diagramaActualizado);

            return diagramaActualizado;
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar la imagen del diagrama: " + e.getMessage(), e);
        }
    }
}
