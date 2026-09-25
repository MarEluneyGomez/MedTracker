package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Dose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DoseRepository extends JpaRepository<Dose, UUID> {

    // Buscar dosis por recordatorio
    List<Dose> findByReminderId(UUID reminderId);

    // Buscar dosis por estado
    List<Dose> findByStatus(Dose.DoseStatus status);
}
