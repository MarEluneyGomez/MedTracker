package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    // Buscar recordatorios por tratamiento
    List<Reminder> findByTreatmentId(UUID treatmentId);

    // Buscar recordatorios activos (los que siguen generando dosis)
    List<Reminder> findByActive(boolean active);
}
