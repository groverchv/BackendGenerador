package uagrm.software.Parcial1.Controllers;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import uagrm.software.Parcial1.Models.DiagramEntity;
import uagrm.software.Parcial1.Models.ProjectEntity;
import uagrm.software.Parcial1.Services.ProjectService;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // --- CRUD Proyectos ---

    @GetMapping
    public List<ProjectEntity> list() {
        return projectService.listarProyectos();
    }

    @GetMapping("/{id}")
    public ProjectEntity get(@PathVariable Long id) {
        ProjectEntity p = projectService.buscarPorId(id);
        if (p == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
        return p;
    }

    @PostMapping
    public ProjectEntity create(@RequestBody ProjectEntity body) {
        // Al guardar el proyecto, también se crea el diagrama por defecto
        return projectService.guardarProyecto(body);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        projectService.eliminarProyecto(id);
    }

    // --- ENDPOINTS para el único diagrama del proyecto ---

    /** Devuelve el diagrama asociado al proyecto */
    @GetMapping("/{id}/diagram")
    public DiagramEntity getDiagram(@PathVariable Long id) {
        ProjectEntity p = projectService.buscarPorId(id);
        if (p == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado");

        DiagramEntity d = p.getDiagram();
        if (d == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagrama no encontrado");
        return d;
    }

    /** Back-compat: devuelve lista con 1 diagrama */
    @GetMapping("/{id}/diagrams")
    public List<DiagramEntity> getDiagramAsList(@PathVariable Long id) {
        ProjectEntity p = projectService.buscarPorId(id);
        if (p == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado");

        DiagramEntity d = p.getDiagram();
        return d == null ? List.of() : List.of(d);
    }

    @PutMapping("/{id}/diagram")
    public DiagramEntity updateDiagram(@PathVariable Long id, @RequestBody DiagramEntity body) {
        return projectService.actualizarDiagrama(id, body);
    }

}
