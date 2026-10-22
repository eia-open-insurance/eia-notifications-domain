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
    private static final UUID SOURCE_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000003");

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
    void createForwardsSourceLinkageUnchanged() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                "NEW_CLAIM_NOTICE", "EMAIL", PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", "CLAIM_NOTICE_STANDARD", 1, null,
                "DIRECT_CLAIM", SOURCE_ID, "CLM-2026-000000001");
        NotificationDto expected = new NotificationDto(NOTIFICATION_ID, TENANT, "NTF-2026-000000001",
                "NEW_CLAIM_NOTICE", "EMAIL", "CREATED", PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", "CLAIM_NOTICE_STANDARD", 1, null, null,
                null, null, 0, Instant.now(), Instant.now(), "DIRECT_CLAIM", SOURCE_ID, "CLM-2026-000000001");
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

    @Test
    void listTrimsAndForwardsSourceFilterToExpert() {
        when(client.findAll(TENANT, "DIRECT_CLAIM", SOURCE_ID.toString())).thenReturn(List.of());

        assertThat(service.list(TENANT, "  DIRECT_CLAIM ", " " + SOURCE_ID + "  ")).isEmpty();

        verify(client).findAll(TENANT, "DIRECT_CLAIM", SOURCE_ID.toString());
        verify(client, never()).findAll(TENANT);
    }

    @Test
    void listUsesTheUnfilteredExpertCallForBlankSourceFilter() {
        when(client.findAll(TENANT)).thenReturn(List.of());

        assertThat(service.list(TENANT, "  ", "")).isEmpty();

        verify(client).findAll(TENANT);
        verify(client, never()).findAll(any(), any(), any());
    }

    @Test
    void listForwardsAnIncompleteSourceFilterSoTheExpertOwnsThePairRule() {
        when(client.findAll(TENANT, "DIRECT_CLAIM", null)).thenThrow(new ExpertApiException(
                400, "EIA-NTF-9412", "Invalid source filter",
                "sourceId is required when sourceType is supplied.", "VALIDATION", false));

        assertThatThrownBy(() -> service.list(TENANT, "DIRECT_CLAIM", null))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(400);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9412");
                    assertThat(ex.getMessage())
                            .isEqualTo("sourceId is required when sourceType is supplied.");
                });

        verify(client).findAll(TENANT, "DIRECT_CLAIM", null);
    }

    @Test
    void attemptsDelegateToTheExpertWithTheTenant() {
        NotificationDeliveryAttemptDto attempt = new NotificationDeliveryAttemptDto(
                UUID.fromString("0192f3a4-0000-7000-8000-0000000000a1"), TENANT, NOTIFICATION_ID, 1,
                "EMAIL", "SIMULATOR", "SIM-" + NOTIFICATION_ID, "DELIVERED", null, null,
                Instant.parse("2026-10-22T12:00:00Z"));
        when(client.findAttempts(TENANT, NOTIFICATION_ID)).thenReturn(List.of(attempt));

        assertThat(service.attempts(TENANT, NOTIFICATION_ID)).containsExactly(attempt);

        verify(client).findAttempts(TENANT, NOTIFICATION_ID);
    }

    @Test
    void attemptsPreserveTheExpertNotFound() {
        when(client.findAttempts(TENANT, NOTIFICATION_ID)).thenThrow(new ExpertApiException(
                404, "EIA-NTF-9001", "Resource not found",
                "Notification '" + NOTIFICATION_ID + "' was not found.", "NOT_FOUND", false));

        assertThatThrownBy(() -> service.attempts(TENANT, NOTIFICATION_ID))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(404);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9001");
                    assertThat(ex.category()).isEqualTo("NOT_FOUND");
                });

        verify(client).findAttempts(TENANT, NOTIFICATION_ID);
    }

    private CreateNotificationCommand command(String channel, String type, String templateCode,
                                               Integer templateVersion) {
        return new CreateNotificationCommand(type, channel, PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", templateCode, templateVersion, null,
                null, null, null);
    }

    private NotificationDto dto(String status, String templateCode, Instant scheduledAt) {
        return new NotificationDto(NOTIFICATION_ID, TENANT, "NTF-2026-000000001", "NEW_CLAIM_NOTICE",
                "EMAIL", status, PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", templateCode,
                templateCode == null ? null : 1, scheduledAt, null, null, null, 0, Instant.now(), Instant.now(),
                null, null, null);
    }
}
