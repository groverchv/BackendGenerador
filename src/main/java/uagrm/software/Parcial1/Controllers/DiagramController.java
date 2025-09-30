package uagrm.software.Parcial1.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import uagrm.software.Parcial1.Models.DiagramEntity;
import uagrm.software.Parcial1.Services.DiagramService;

@RestController
@RequestMapping("/api/diagrams")
@RequiredArgsConstructor
public class DiagramController {
    private final DiagramService diagramService;

    @GetMapping("/{id}")
    public DiagramEntity get(@PathVariable Long id) {
        DiagramEntity d = diagramService.buscarPorId(id);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagrama no encontrado");
        return d;
    }
}
