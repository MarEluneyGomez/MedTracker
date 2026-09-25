package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    // Buscar recordatorios por tratamiento
    List<Reminder> findByTreatmentId(UUID treatmentId);

    // Buscar recordatorios completados
    List<Reminder> findByCompleted(boolean completed);

    // Buscar recordatorios por fecha y hora exacta
    List<Reminder> findByDateTime(LocalDateTime dateTime);
}
