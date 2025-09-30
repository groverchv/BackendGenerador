package uagrm.software.Parcial1.Models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@Entity
@Table(name = "diagrams")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class DiagramEntity {

    @Id
    private Long id; // = projects.id (PK compartida)

    // Dueño de la relación. @MapsId hace que 'id' copie el id del proyecto.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id")     // FK = PK
    @JsonIgnore
    @ToString.Exclude
    private ProjectEntity project;

    @Column(nullable = false, length = 160)
    private String name = "Nuevo Diagrama";

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String nodes = "[]";

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String edges = "[]";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String viewport;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
