package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.CaregiverLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaregiverLinkRepository extends JpaRepository<CaregiverLink, UUID> {

    // Buscar enlaces por caregiver
    List<CaregiverLink> findByCaregiverId(UUID caregiverId);

    // Buscar enlaces por paciente
    List<CaregiverLink> findByPatientId(UUID patientId);
}
