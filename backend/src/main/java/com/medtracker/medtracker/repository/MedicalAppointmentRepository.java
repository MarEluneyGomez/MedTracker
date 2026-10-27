package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.MedicalAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalAppointmentRepository extends JpaRepository<MedicalAppointment, UUID> {

    // Buscar citas por paciente
    List<MedicalAppointment> findByPatientId(UUID patientId);

    // Buscar citas por médico
    List<MedicalAppointment> findByDoctorId(UUID doctorId);

    // Cita vigente por id (las eliminadas lógicamente no se encuentran)
    Optional<MedicalAppointment> findByIdAndDeletedAtIsNull(UUID id);

    List<MedicalAppointment> findByPatientIdAndDeletedAtIsNull(UUID patientId);

    List<MedicalAppointment> findByDoctorIdAndDeletedAtIsNull(UUID doctorId);

    // Citas vigentes en las que el usuario es el paciente o el médico
    @Query("""
            select a from MedicalAppointment a
            where (a.patient.id = :userId or a.doctor.id = :userId) and a.deletedAt is null
            """)
    List<MedicalAppointment> findByParticipant(@Param("userId") UUID userId);

    // ¿Hay una cita vigente entre ese paciente y ese médico?
    boolean existsByPatientIdAndDoctorIdAndDeletedAtIsNull(UUID patientId, UUID doctorId);

    // ¿Hay una cita vigente y no cancelada entre ese médico y ese paciente? (acceso al historial médico)
    @Query("""
            select count(a) > 0 from MedicalAppointment a
            where a.doctor.id = :doctorId and a.patient.id = :patientId
              and a.deletedAt is null and a.status <> com.medtracker.medtracker.model.MedicalAppointment.AppointmentStatus.CANCELLED
            """)
    boolean existsActiveByDoctorAndPatient(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId);

    // Pacientes con los que el médico tiene una cita vigente y no cancelada
    @Query("""
            select distinct a.patient.id from MedicalAppointment a
            where a.doctor.id = :doctorId
              and a.deletedAt is null and a.status <> com.medtracker.medtracker.model.MedicalAppointment.AppointmentStatus.CANCELLED
            """)
    List<UUID> findActivePatientIdsByDoctor(@Param("doctorId") UUID doctorId);

    // Buscar citas por estado
    List<MedicalAppointment> findByStatus(MedicalAppointment.AppointmentStatus status);
}
