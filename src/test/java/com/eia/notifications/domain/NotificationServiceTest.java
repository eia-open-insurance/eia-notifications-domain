package com.eia.notifications.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID NOTIFICATION_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final UUID PARTY_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000002");

    @Mock
    private NotificationExpertClient client;

    @InjectMocks
    private NotificationService service;

    @Test
    void createRejectsChannelOutsideSpec() {
        CreateNotificationCommand command = command("FAX", "NEW_CLAIM_NOTICE", null, null);

        assertThatThrownBy(() -> service.create(TENANT, command))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_CHANNEL));

        verify(client, never()).create(any(), any());
    }

    @Test
    void createRejectsTypeOutsideSpec() {
        CreateNotificationCommand command = command("EMAIL", "NOT_A_TYPE", null, null);

        assertThatThrownBy(() -> service.create(TENANT, command))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_NOTIFICATION_TYPE));
    }

    @Test
    void createRejectsTemplateVersionWithoutCode() {
        CreateNotificationCommand command = command("EMAIL", "MENTION", null, 2);

        assertThatThrownBy(() -> service.create(TENANT, command))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.TEMPLATE_REQUIRED));
    }

    @Test
    void createRejectsNonPositiveTemplateVersion() {
        CreateNotificationCommand command = command("EMAIL", "MENTION", "CLAIM_NOTICE_STANDARD", 0);

        assertThatThrownBy(() -> service.create(TENANT, command))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_TEMPLATE_VERSION));
    }

    @Test
    void createDelegatesToExpert() {
        CreateNotificationCommand command = command("EMAIL", "NEW_CLAIM_NOTICE", "CLAIM_NOTICE_STANDARD", 1);
        NotificationDto expected = dto("CREATED", "CLAIM_NOTICE_STANDARD", null);
        when(client.create(TENANT, command)).thenReturn(expected);

        assertThat(service.create(TENANT, command)).isEqualTo(expected);
        verify(client).create(TENANT, command);
    }

    @Test
    void sendRejectsMissingTemplate() {
        when(client.findById(TENANT, NOTIFICATION_ID)).thenReturn(dto("CREATED", null, null));

        assertThatThrownBy(() -> service.send(TENANT, NOTIFICATION_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.TEMPLATE_REQUIRED));

        verify(client, never()).send(any(), any());
    }

    @Test
    void sendRejectsFutureSchedule() {
        when(client.findById(TENANT, NOTIFICATION_ID))
                .thenReturn(dto("CREATED", "CLAIM_NOTICE_STANDARD", Instant.now().plusSeconds(3600)));

        assertThatThrownBy(() -> service.send(TENANT, NOTIFICATION_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.SEND_BEFORE_SCHEDULED));

        verify(client, never()).send(any(), any());
    }

    @Test
    void sendRejectsNonSendableStatus() {
        when(client.findById(TENANT, NOTIFICATION_ID))
                .thenReturn(dto("SENT", "CLAIM_NOTICE_STANDARD", null));

        assertThatThrownBy(() -> service.send(TENANT, NOTIFICATION_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void sendDelegatesWhenInvariantsHold() {
        when(client.findById(TENANT, NOTIFICATION_ID))
                .thenReturn(dto("CREATED", "CLAIM_NOTICE_STANDARD", Instant.now().minusSeconds(60)));
        NotificationDto sent = dto("SENT", "CLAIM_NOTICE_STANDARD", Instant.now().minusSeconds(60));
        when(client.send(TENANT, NOTIFICATION_ID)).thenReturn(sent);

        assertThat(service.send(TENANT, NOTIFICATION_ID)).isEqualTo(sent);
        verify(client).send(TENANT, NOTIFICATION_ID);
    }

    @Test
    void listDelegatesToExpert() {
        when(client.findAll(TENANT)).thenReturn(List.of());

        assertThat(service.list(TENANT)).isEmpty();
    }

    private CreateNotificationCommand command(String channel, String type, String templateCode,
                                               Integer templateVersion) {
        return new CreateNotificationCommand(type, channel, PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", templateCode, templateVersion, null);
    }

    private NotificationDto dto(String status, String templateCode, Instant scheduledAt) {
        return new NotificationDto(NOTIFICATION_ID, TENANT, "NTF-2026-000000001", "NEW_CLAIM_NOTICE",
                "EMAIL", status, PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", templateCode,
                templateCode == null ? null : 1, scheduledAt, null, 0, Instant.now(), Instant.now());
    }
}
