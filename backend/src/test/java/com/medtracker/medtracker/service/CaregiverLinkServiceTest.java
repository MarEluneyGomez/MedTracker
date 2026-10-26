package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaregiverLinkServiceTest {

    @Mock
    private CaregiverLinkRepository caregiverLinkRepository;
    @Mock
    private UserService userService;

    private CaregiverLinkService service;
    private final User caregiver = User.builder().id(UUID.randomUUID()).role(User.Role.CAREGIVER).build();
    private final User patient = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
    private CaregiverLinkId linkId;

    @BeforeEach
    void setUp() {
        service = new CaregiverLinkService(caregiverLinkRepository, userService);
        linkId = new CaregiverLinkId(caregiver.getId(), patient.getId());
        when(userService.getWithRole(any(), org.mockito.ArgumentMatchers.eq(User.Role.CAREGIVER))).thenReturn(caregiver);
        when(userService.getWithRole(any(), org.mockito.ArgumentMatchers.eq(User.Role.PATIENT))).thenReturn(patient);
    }

    private CaregiverLink incoming() {
        return CaregiverLink.builder().caregiver(caregiver).patient(patient).build();
    }

    @Test
    void registerCreatesNewLinkWhenNoneExists() {
        CaregiverLink link = incoming();
        when(caregiverLinkRepository.findById(linkId)).thenReturn(Optional.empty());
        when(caregiverLinkRepository.save(link)).thenAnswer(inv -> inv.getArgument(0));

        CaregiverLink saved = service.register(link);

        assertThat(saved.getId()).isEqualTo(linkId);
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void registerReturns409WhenActiveLinkAlreadyExists() {
        CaregiverLink active = incoming();
        active.setId(linkId);
        when(caregiverLinkRepository.findById(linkId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.register(incoming()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(caregiverLinkRepository, never()).save(any());
    }

    @Test
    void registerRevivesSoftDeletedLinkInsteadOfInsertingANewOne() {
        CaregiverLink deleted = incoming();
        deleted.setId(linkId);
        deleted.setDeletedAt(LocalDateTime.now());
        when(caregiverLinkRepository.findById(linkId)).thenReturn(Optional.of(deleted));
        when(caregiverLinkRepository.save(deleted)).thenAnswer(inv -> inv.getArgument(0));

        CaregiverLink result = service.register(incoming());

        assertThat(result).isSameAs(deleted);
        assertThat(result.getDeletedAt()).isNull();
    }
}
