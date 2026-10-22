package com.eia.notifications.domain;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID NOTIFICATION_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final UUID PARTY_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000002");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationService service;

    @Test
    void listReturnsNotifications() throws Exception {
        when(service.list(TENANT)).thenReturn(List.of(dto("CREATED", "CLAIM_NOTICE_STANDARD")));

        mvc.perform(get("/api/v1/notifications").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].notificationNumber").value("NTF-2026-000000001"))
                .andExpect(jsonPath("$[0].status").value("CREATED"));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(eq(TENANT), any(CreateNotificationCommand.class)))
                .thenReturn(dto("CREATED", "CLAIM_NOTICE_STANDARD"));

        mvc.perform(post("/api/v1/notifications")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notificationType": "NEW_CLAIM_NOTICE",
                                  "channel": "EMAIL",
                                  "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                                  "subject": "Claim requires attention",
                                  "body": "Claim CLM-2026-000000001 requires attention.",
                                  "templateCode": "CLAIM_NOTICE_STANDARD",
                                  "templateVersion": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationNumber").value("NTF-2026-000000001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.sourceType").value(nullValue()))
                .andExpect(jsonPath("$.sourceId").value(nullValue()))
                .andExpect(jsonPath("$.sourceReference").value(nullValue()));

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(service).create(eq(TENANT), captor.capture());
        assertThat(captor.getValue().sourceType()).isNull();
        assertThat(captor.getValue().sourceId()).isNull();
        assertThat(captor.getValue().sourceReference()).isNull();
    }

    @Test
    void createCarriesSourceLinkage() throws Exception {
        when(service.create(eq(TENANT), any(CreateNotificationCommand.class)))
                .thenReturn(dtoWithSource("DIRECT_CLAIM", PARTY_ID, "CLM-2026-000000001"));

        mvc.perform(post("/api/v1/notifications")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notificationType": "NEW_CLAIM_NOTICE",
                                  "channel": "EMAIL",
                                  "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                                  "subject": "Claim requires attention",
                                  "body": "Claim CLM-2026-000000001 requires attention.",
                                  "templateCode": "CLAIM_NOTICE_STANDARD",
                                  "templateVersion": 1,
                                  "sourceType": "DIRECT_CLAIM",
                                  "sourceId": "0192f3a4-0000-7000-8000-000000000002",
                                  "sourceReference": "CLM-2026-000000001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$.sourceId").value(PARTY_ID.toString()))
                .andExpect(jsonPath("$.sourceReference").value("CLM-2026-000000001"));

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(service).create(eq(TENANT), captor.capture());
        assertThat(captor.getValue().sourceType()).isEqualTo("DIRECT_CLAIM");
        assertThat(captor.getValue().sourceId()).isEqualTo(PARTY_ID);
        assertThat(captor.getValue().sourceReference()).isEqualTo("CLM-2026-000000001");
    }

    @Test
    void createPreservesExpertSourceLinkageBadRequest() throws Exception {
        when(service.create(eq(TENANT), any(CreateNotificationCommand.class)))
                .thenThrow(new ExpertApiException(400, "EIA-NTF-9404", "Request validation failed",
                        "sourceId is required when sourceType is supplied.", "VALIDATION", false));

        mvc.perform(post("/api/v1/notifications")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notificationType": "NEW_CLAIM_NOTICE",
                                  "channel": "EMAIL",
                                  "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                                  "subject": "Claim requires attention",
                                  "body": "Claim CLM-2026-000000001 requires attention.",
                                  "sourceType": "DIRECT_CLAIM"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9404"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.detail").value("sourceId is required when sourceType is supplied."))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void createRejectsInvalidChannelWithStructured400() throws Exception {
        when(service.create(eq(TENANT), any(CreateNotificationCommand.class)))
                .thenThrow(new ApiException(ErrorCode.INVALID_CHANNEL, "Channel 'FAX' is not allowed."));

        mvc.perform(post("/api/v1/notifications")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notificationType": "NEW_CLAIM_NOTICE",
                                  "channel": "FAX",
                                  "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                                  "subject": "Claim requires attention",
                                  "body": "Claim CLM-2026-000000001 requires attention."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9401"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void createValidationFailureListsFieldViolations() throws Exception {
        mvc.perform(post("/api/v1/notifications")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notificationType": "NEW_CLAIM_NOTICE",
                                  "channel": "EMAIL",
                                  "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                                  "subject": "",
                                  "body": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9404"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").exists());
    }

    @Test
    void missingTenantHeaderReturnsStructured400() throws Exception {
        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9501"));
    }

    @Test
    void invalidTenantHeaderReturnsStructured400() throws Exception {
        mvc.perform(get("/api/v1/notifications").header("X-EIA-Tenant", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9502"));
    }

    @Test
    void unknownNotificationPropagatesExpert404() throws Exception {
        when(service.get(TENANT, NOTIFICATION_ID)).thenThrow(new ExpertApiException(
                404, "EIA-NTF-9001", "Resource not found",
                "Notification '" + NOTIFICATION_ID + "' was not found.", "NOT_FOUND", false));

        mvc.perform(get("/api/v1/notifications/{id}", NOTIFICATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9001"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void sendRejectsFutureScheduleWithStructured400() throws Exception {
        when(service.send(TENANT, NOTIFICATION_ID)).thenThrow(new ApiException(
                ErrorCode.SEND_BEFORE_SCHEDULED,
                "Notification is scheduled for a future instant; sent_at must not precede scheduled_at."));

        mvc.perform(post("/api/v1/notifications/{id}/send", NOTIFICATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9301"))
                .andExpect(jsonPath("$.category").value("BUSINESS_RULE"));
    }

    private NotificationDto dto(String status, String templateCode) {
        return new NotificationDto(NOTIFICATION_ID, TENANT, "NTF-2026-000000001", "NEW_CLAIM_NOTICE",
                "EMAIL", status, PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", templateCode,
                templateCode == null ? null : 1, null, null, 0, Instant.now(), Instant.now(),
                null, null, null);
    }

    private NotificationDto dtoWithSource(String sourceType, UUID sourceId, String sourceReference) {
        return new NotificationDto(NOTIFICATION_ID, TENANT, "NTF-2026-000000001", "NEW_CLAIM_NOTICE",
                "EMAIL", "CREATED", PARTY_ID, "Claim requires attention",
                "Claim CLM-2026-000000001 requires attention.", "CLAIM_NOTICE_STANDARD",
                1, null, null, 0, Instant.now(), Instant.now(),
                sourceType, sourceId, sourceReference);
    }
}
