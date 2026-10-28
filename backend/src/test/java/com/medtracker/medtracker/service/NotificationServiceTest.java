package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Notification;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.NotificationRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private AccessPolicy accessPolicy;
    @InjectMocks
    private NotificationService service;

    private final User user = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
    private final AuthenticatedUser principal = new AuthenticatedUser(user.getId(), User.Role.PATIENT);

    private Notification notification() {
        return Notification.builder().id(UUID.randomUUID()).user(user).message("Tomar").build();
    }

    @Test
    void markAsReadSetsTheFlagOnTheOwnNotification() {
        Notification notification = notification();
        when(accessPolicy.requireNotification(principal, notification.getId())).thenReturn(notification);
        when(repository.save(notification)).thenReturn(notification);

        assertThat(service.markAsRead(principal, notification.getId()).isRead()).isTrue();
    }

    @Test
    void markAsReadOnAForeignNotificationIs404AndNothingIsSaved() {
        UUID id = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(accessPolicy).requireNotification(principal, id);

        assertThatThrownBy(() -> service.markAsRead(principal, id)).isInstanceOfSatisfying(
                ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repository, never()).save(any());
    }

    @Test
    void findAllOnlyQueriesTheCallersNotifications() {
        List<Notification> own = List.of(notification());
        when(repository.findByUserIdAndDeletedAtIsNull(user.getId())).thenReturn(own);

        assertThat(service.findAll(principal)).isSameAs(own);
    }

    @Test
    void findByUserIs403ForAnotherUserAndNeverQueries() {
        UUID other = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(accessPolicy).requireSelf(principal, other);

        assertThatThrownBy(() -> service.findByUser(principal, other)).isInstanceOfSatisfying(
                ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).findByUserIdAndDeletedAtIsNull(other);
    }

    @Test
    void findByUserReturnsTheOwnNotifications() {
        List<Notification> own = List.of(notification());
        when(repository.findByUserIdAndDeletedAtIsNull(user.getId())).thenReturn(own);

        assertThat(service.findByUser(principal, user.getId())).isSameAs(own);
        verify(accessPolicy).requireSelf(principal, user.getId());
    }
}
