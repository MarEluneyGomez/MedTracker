package com.medtracker.medtracker.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDeactivationRequestDTOTest {

    @Test
    void toStringNeverContainsThePassword() {
        String password = "clave-de-prueba";

        assertThat(new AccountDeactivationRequestDTO(password).toString()).doesNotContain(password);
    }
}
