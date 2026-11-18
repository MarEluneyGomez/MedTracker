package com.medtracker.medtracker.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Cómo deserializa el mapper real de Spring Boot (Jackson 3) el confirmedAt del body de
// PUT /doses/{id}/confirm. Solo se fija el comportamiento correcto.
@JsonTest
class DoseConfirmRequestDTOJsonTest {

    @Autowired
    private ObjectMapper mapper;

    private DoseConfirmRequestDTO read(String json) {
        return mapper.readValue(json, DoseConfirmRequestDTO.class);
    }

    @Test
    void aLocalDateTimeWithoutZoneIsRead() {
        assertThat(read("{\"confirmedAt\":\"2026-11-18T08:05:00\"}").getConfirmedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 8, 5));
    }

    @Test
    void extraFieldsAreIgnored() {
        assertThat(read("{\"confirmedAt\":\"2026-11-18T08:05:00\",\"extra\":1}").getConfirmedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 8, 5));
    }

    @Test
    void aTimeWithAnOffsetIsRejected() {
        assertThatThrownBy(() -> read("{\"confirmedAt\":\"2026-11-18T08:05:00-03:00\"}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void aTimeWithMillisecondsIsRead() {
        assertThat(read("{\"confirmedAt\":\"2026-11-18T08:05:00.123\"}").getConfirmedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 8, 5, 0, 123_000_000));
    }

    @Test
    void aTimeWithTheZSuffixIsRejected() {
        assertThatThrownBy(() -> read("{\"confirmedAt\":\"2026-11-18T08:05:00Z\"}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void aTimeWithoutSecondsIsRejected() {
        assertThatThrownBy(() -> read("{\"confirmedAt\":\"2026-11-18T08:05\"}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void aValueThatIsNotADateTimeIsRejected() {
        assertThatThrownBy(() -> read("{\"confirmedAt\":\"ayer\"}"))
                .isInstanceOf(JacksonException.class);
    }
}
