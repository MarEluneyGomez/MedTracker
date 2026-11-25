package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.MedicalAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // ¿Hay una cita vigente y no cancelada entre ese médico y ese paciente? Hoy se usa solo en el
    // sentido paciente -> médico de AccessPolicy.shareAppointment. NO usarla para dar acceso al
    // médico: médico -> paciente y el historial exigen CONFIRMED (existsConfirmedByDoctorAndPatient).
    @Query("""
            select count(a) > 0 from MedicalAppointment a
            where a.doctor.id = :doctorId and a.patient.id = :patientId
              and a.deletedAt is null and a.status <> com.medtracker.medtracker.model.MedicalAppointment.AppointmentStatus.CANCELLED
            """)
    boolean existsActiveByDoctorAndPatient(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId);

    // ¿Hay una cita vigente y CONFIRMED por el paciente entre ese médico y ese paciente? (acceso al historial médico)
    @Query("""
            select count(a) > 0 from MedicalAppointment a
            where a.doctor.id = :doctorId and a.patient.id = :patientId
              and a.deletedAt is null and a.status = com.medtracker.medtracker.model.MedicalAppointment.AppointmentStatus.CONFIRMED
            """)
    boolean existsConfirmedByDoctorAndPatient(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId);

    // Pacientes con los que el médico tiene una cita vigente y CONFIRMED
    @Query("""
            select distinct a.patient.id from MedicalAppointment a
            where a.doctor.id = :doctorId
              and a.deletedAt is null and a.status = com.medtracker.medtracker.model.MedicalAppointment.AppointmentStatus.CONFIRMED
            """)
    List<UUID> findConfirmedPatientIdsByDoctor(@Param("doctorId") UUID doctorId);

    // Buscar citas por estado
    List<MedicalAppointment> findByStatus(MedicalAppointment.AppointmentStatus status);

    // Cambia el estado salvo que la cita ya esté cancelada o eliminada; devuelve las
    // filas afectadas (0 = otra petición la canceló antes). No comprueba quién llama:
    // hay que autorizar antes con requireAppointment. Un UPDATE masivo no dispara
    // @UpdateTimestamp, por eso se asigna updatedAt a mano.
    // Ojo: flushAutomatically y clearAutomatically se acoplan a una transacción externa si algún
    // día el servicio abre una (hoy no la hay): desligarían todas las entidades gestionadas y
    // vaciarían cambios pendientes antes de tiempo. Es el mismo patrón que DoseRepository.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MedicalAppointment a set a.status = :status, a.updatedAt = :now
            where a.id = :id and a.deletedAt is null
              and a.status <> com.medtracker.medtracker.model.MedicalAppointment.AppointmentStatus.CANCELLED
            """)
    int updateStatusUnlessCancelled(@Param("id") UUID id,
                                    @Param("status") MedicalAppointment.AppointmentStatus status,
                                    @Param("now") LocalDateTime now);
}
