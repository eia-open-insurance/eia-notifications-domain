package com.eia.notifications.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(NotificationService.class)
class NotificationSearchTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("99999999-9999-7999-8999-999999999999");
    private static final UUID NOTIFICATION_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final UUID RECIPIENT = UUID.fromString("0192f3a4-0000-7000-8000-000000000002");
    private static final Instant BASE = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationExpertClient client;

    @Test
    void matchesByNotificationNumber() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                notification(NOTIFICATION_ID, TENANT, "NTF-2026-000000001", "Claim received", "EMAIL",
                        "CREATED", RECIPIENT, BASE),
                notification(UUID.randomUUID(), TENANT, "NTF-2026-000000002", "Policy bound", "EMAIL",
                        "SENT", UUID.randomUUID(), BASE.plusSeconds(5))));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("q", "ntf-2026-000000001")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    void matchesBySubjectCaseInsensitively() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                notification(NOTIFICATION_ID, TENANT, "NTF-2026-000000001", "Claim received", "EMAIL",
                        "CREATED", RECIPIENT, BASE),
                notification(UUID.randomUUID(), TENANT, "NTF-2026-000000002", "Policy bound", "EMAIL",
                        "SENT", UUID.randomUUID(), BASE)));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("q", "CLAIM received")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    void matchesByRecipientPartyIdContains() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                notification(NOTIFICATION_ID, TENANT, "NTF-2026-000000001", "Claim received", "EMAIL",
                        "CREATED", RECIPIENT, BASE),
                notification(UUID.randomUUID(), TENANT, "NTF-2026-000000002", "Policy bound", "EMAIL",
                        "SENT", UUID.fromString("0192f3a4-0000-7000-8000-000000000099"), BASE)));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("q", "0000-7000-8000-000000000002")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    void blankQueryReturnsLatestTenByUpdatedAtDesc() throws Exception {
        when(client.findAll(TENANT)).thenReturn(notifications(12));

        mvc.perform(get("/api/v1/notifications/search").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value(""))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].label").value("NTF-2026-000000012"))
                .andExpect(jsonPath("$.items[9].label").value("NTF-2026-000000003"));
    }

    @Test
    void appliesRequestedLimit() throws Exception {
        when(client.findAll(TENANT)).thenReturn(notifications(5));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("limit", "2")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].label").value("NTF-2026-000000005"))
                .andExpect(jsonPath("$.items[1].label").value("NTF-2026-000000004"));
    }

    @Test
    void capsLimitAtTwentyFive() throws Exception {
        when(client.findAll(TENANT)).thenReturn(notifications(30));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("limit", "100")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.items.length()").value(25));
    }

    @Test
    void clampsNonPositiveLimit() throws Exception {
        when(client.findAll(TENANT)).thenReturn(notifications(3));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("limit", "0")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void doesNotReturnAnotherTenantsNotifications() throws Exception {
        NotificationDto mine = notification(NOTIFICATION_ID, TENANT, "NTF-2026-000000001",
                "Claim received", "EMAIL", "CREATED", RECIPIENT, BASE);
        NotificationDto foreign = notification(UUID.randomUUID(), OTHER_TENANT, "NTF-2026-000000099",
                "Claim received again", "EMAIL", "SENT", RECIPIENT, BASE.plusSeconds(30));
        when(client.findAll(TENANT)).thenReturn(List.of(mine, foreign));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("q", "claim")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(NOTIFICATION_ID.toString()));

        verify(client).findAll(TENANT);
    }

    @Test
    void returnsCommonProjectionAndHref() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(notification(NOTIFICATION_ID, TENANT,
                "NTF-2026-000000001", "Claim received", "EMAIL", "CREATED", RECIPIENT, BASE)));

        mvc.perform(get("/api/v1/notifications/search")
                        .param("q", "claim")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("claim"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.items[0].id").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.items[0].type").value("NOTIFICATION"))
                .andExpect(jsonPath("$.items[0].label").value("NTF-2026-000000001"))
                .andExpect(jsonPath("$.items[0].secondary").value("Claim received · EMAIL"))
                .andExpect(jsonPath("$.items[0].status").value("CREATED"))
                .andExpect(jsonPath("$.items[0].href").value("/notifications?record=" + NOTIFICATION_ID));
    }

    @Test
    void rejectsRequestsWithoutTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/notifications/search").param("q", "claim"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EIA-NTF-9501"));
    }

    private static List<NotificationDto> notifications(int count) {
        List<NotificationDto> notifications = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            notifications.add(notification(UUID.randomUUID(), TENANT,
                    "NTF-2026-%09d".formatted(i), "Subject " + i, "EMAIL", "CREATED", RECIPIENT,
                    BASE.plusSeconds(i)));
        }
        return notifications;
    }

    private static NotificationDto notification(UUID id, UUID tenantId, String number, String subject,
                                                String channel, String status, UUID recipient,
                                                Instant updatedAt) {
        return new NotificationDto(id, tenantId, number, "NEW_CLAIM_NOTICE", channel, status,
                recipient, subject, "Body", "CLAIM_NOTICE_STANDARD", 1, null, null, 0, BASE,
                updatedAt);
    }
}
