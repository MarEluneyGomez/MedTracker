package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

// Patrón recurrente de recordatorio para un tratamiento: una hora del día
// (y, opcionalmente, días de la semana puntuales) que se repite mientras el
// tratamiento esté vigente. NO es una fila por cada toma: para "4 veces al
// día" se crean hasta 4 Reminder (uno por horario), no uno por cada toma.
// Las tomas individuales se modelan en Dose, generadas a partir de este
// patrón (ver Dose.java).
@Entity
@Table(name = "reminder")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación con Treatment
    @ManyToOne
    @JoinColumn(name = "treatment_id", nullable = false)
    private Treatment treatment;

    // Hora del día en que corresponde la toma
    @Column(nullable = false)
    private LocalTime time;

    // Días de la semana en que aplica (ej. {"MON","WED","FRI"}); null = todos los días
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", columnDefinition = "varchar[]")
    private String[] daysOfWeek;

    // Texto del recordatorio
    @Column(nullable = false)
    private String message;

    // Si el recordatorio sigue generando dosis; arranca activo
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Auditoría
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
