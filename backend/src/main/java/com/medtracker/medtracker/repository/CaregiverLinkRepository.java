package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaregiverLinkRepository extends JpaRepository<CaregiverLink, CaregiverLinkId> {

    // Buscar enlaces por caregiver (navega la relación "caregiver" -> su id)
    List<CaregiverLink> findByCaregiver_Id(UUID caregiverId);

    // Buscar enlaces por paciente
    List<CaregiverLink> findByPatient_Id(UUID patientId);
}
