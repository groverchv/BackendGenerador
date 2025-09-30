package uagrm.software.Parcial1.Models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "package_base", length = 160)
    private String packageBase;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt;

    // 1 Proyecto -> 1 Diagrama (PK compartida). Al borrar proyecto, borra el diagrama.
    @OneToOne(mappedBy = "project",
              cascade = CascadeType.ALL, // 🔑 guarda y elimina en cascada
              orphanRemoval = true,
              fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private DiagramEntity diagram;

    /** (Opcional) Exponer solo el id del diagrama en JSON del proyecto */
    @Transient
    public Long getDiagramId() {
        return diagram != null ? diagram.getId() : null;
    }

    // Helper para setear ambos lados de la relación
    public void setDiagram(DiagramEntity diagram) {
        this.diagram = diagram;
        if (diagram != null) {
            diagram.setProject(this);
        }
        this.lastEditedAt = LocalDateTime.now();
    }
}
