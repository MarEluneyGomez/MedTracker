package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "medication")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medication {

    // Catálogo compartido, sin dueño ni dato sensible: usa autoincremental
    // (BIGSERIAL) en vez de UUID, a diferencia del resto de las entidades.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre del medicamento
    @Column(nullable = false)
    private String name;

    // Forma de administración (oral, inyectable, tópico, etc.)
    @Column(name = "administration_form", nullable = false)
    private String administrationForm;

    // Fechas de auditoría
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
