package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TreatmentDTOTest {

    private Treatment treatment() {
        return Treatment.builder().id(UUID.randomUUID())
                .user(User.builder().id(UUID.randomUUID()).build())
                .medication(Medication.builder().id(1).build())
                .dosage("1 comp").frequency("cada 8 hs")
                .startDate(LocalDateTime.of(2026, 10, 1, 8, 0)).build();
    }

    @Test
    void treatmentDtoReportsHasActiveRemindersTrueAndFalse() {
        Treatment treatment = treatment();

        TreatmentDTO with = new TreatmentDTO(treatment, true);
        TreatmentDTO without = new TreatmentDTO(treatment, false);

        assertThat(with.isHasActiveReminders()).isTrue();
        assertThat(without.isHasActiveReminders()).isFalse();
        // el resto de los campos sale de la entidad, igual que antes
        assertThat(with.getId()).isEqualTo(treatment.getId());
        assertThat(with.isCompleted()).isFalse();
    }
}
