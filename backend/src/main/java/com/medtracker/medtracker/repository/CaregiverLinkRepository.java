package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CaregiverLinkRepository extends JpaRepository<CaregiverLink, CaregiverLinkId> {

    // Buscar enlaces por caregiver (navega la relación "caregiver" -> su id)
    List<CaregiverLink> findByCaregiver_Id(UUID caregiverId);

    // Vínculos vigentes (sin baja lógica)
    List<CaregiverLink> findByCaregiver_IdAndDeletedAtIsNull(UUID caregiverId);

    List<CaregiverLink> findByPatient_IdAndDeletedAtIsNull(UUID patientId);

    // Buscar enlaces por paciente
    List<CaregiverLink> findByPatient_Id(UUID patientId);

    // Las tres consultas "BetweenActiveUsers" exigen vínculo vigente y que ni el
    // cuidador ni el paciente estén dados de baja
    @Query("""
            select count(l) > 0 from CaregiverLink l
            where l.caregiver.id = :caregiverId and l.patient.id = :patientId and l.deletedAt is null
              and l.caregiver.deletedAt is null and l.patient.deletedAt is null
            """)
    boolean existsActiveBetweenActiveUsers(@Param("caregiverId") UUID caregiverId,
                                           @Param("patientId") UUID patientId);

    @Query("""
            select l from CaregiverLink l
            where l.caregiver.id = :caregiverId and l.deletedAt is null
              and l.caregiver.deletedAt is null and l.patient.deletedAt is null
            """)
    List<CaregiverLink> findByCaregiverBetweenActiveUsers(@Param("caregiverId") UUID caregiverId);

    @Query("""
            select l from CaregiverLink l
            where l.patient.id = :patientId and l.deletedAt is null
              and l.caregiver.deletedAt is null and l.patient.deletedAt is null
            """)
    List<CaregiverLink> findByPatientBetweenActiveUsers(@Param("patientId") UUID patientId);

    // Baja de usuario: da de baja sus vínculos vigentes, como cuidador o como
    // paciente. Un UPDATE masivo no dispara @UpdateTimestamp, por eso se asigna
    // updatedAt a mano. Devuelve las filas afectadas.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CaregiverLink l set l.deletedAt = :now, l.updatedAt = :now
            where l.deletedAt is null and (l.caregiver.id = :userId or l.patient.id = :userId)
            """)
    int softDeleteAllByUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
