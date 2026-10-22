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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public API tests for notification template management. The domain service is
 * mocked so the routes, tenant header, status/code filters, validation, and
 * problem preservation are exercised without a running expert.
 */
@WebMvcTest(NotificationTemplateController.class)
class NotificationTemplateControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID TEMPLATE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String APPROVER = "a-adjuster";
    private static final Instant CREATED_AT = Instant.parse("2026-10-19T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-21T18:00:00Z");
    private static final String BODY_TEMPLATE = "Claim {{claimNumber}} requires attention.";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationTemplateService service;

    @Test
    void listsTemplatesWithStatusAndCodeFilters() throws Exception {
        when(service.list(TENANT, "DRAFT", "CLAIM_NOTICE_STANDARD"))
                .thenReturn(List.of(template("DRAFT", null, null)));

        mvc.perform(get("/api/v1/notifications/templates")
                        .param("status", "DRAFT")
                        .param("code", "CLAIM_NOTICE_STANDARD")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(TEMPLATE_ID.toString()))
                .andExpect(jsonPath("$[0].code").value("CLAIM_NOTICE_STANDARD"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].locale").value("en-US"))
                .andExpect(jsonPath("$[0].bodyTemplate").value(BODY_TEMPLATE))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));

        verify(service).list(TENANT, "DRAFT", "CLAIM_NOTICE_STANDARD");
    }

    @Test
    void listsAllTemplatesWhenFiltersAreAbsent() throws Exception {
        when(service.list(TENANT, null, null)).thenReturn(List.of());

        mvc.perform(get("/api/v1/notifications/templates")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).list(TENANT, null, null);
    }

    @Test
    void createsATemplateAndReturns201() throws Exception {
        when(service.create(eq(TENANT), any(CreateNotificationTemplateCommand.class)))
                .thenReturn(template("DRAFT", null, null));

        mvc.perform(post("/api/v1/notifications/templates")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "CLAIM_NOTICE_STANDARD",
                                  "templateVersion": 1,
                                  "channel": "EMAIL",
                                  "locale": "en-US",
                                  "bodyTemplate": "Claim {{claimNumber}} requires attention."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CLAIM_NOTICE_STANDARD"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.bodyTemplate").value(BODY_TEMPLATE));

        ArgumentCaptor<CreateNotificationTemplateCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationTemplateCommand.class);
        verify(service).create(eq(TENANT), captor.capture());
        CreateNotificationTemplateCommand command = captor.getValue();
        assertThat(command.templateCode()).isEqualTo("CLAIM_NOTICE_STANDARD");
        assertThat(command.templateVersion()).isEqualTo(1);
        assertThat(command.channel()).isEqualTo("EMAIL");
        assertThat(command.locale()).isEqualTo("en-US");
        assertThat(command.bodyTemplate()).isEqualTo(BODY_TEMPLATE);
    }

    @Test
    void createRejectsABlankTemplateCodeWithAStructured400() throws Exception {
        mvc.perform(post("/api/v1/notifications/templates")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": " ",
                                  "templateVersion": 1,
                                  "locale": "en-US",
                                  "bodyTemplate": "Claim {{claimNumber}} requires attention."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9404"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("templateCode"));

        verifyNoInteractions(service);
    }

    @Test
    void createRejectsANonPositiveTemplateVersion() throws Exception {
        mvc.perform(post("/api/v1/notifications/templates")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "CLAIM_NOTICE_STANDARD",
                                  "templateVersion": 0,
                                  "locale": "en-US",
                                  "bodyTemplate": "Claim {{claimNumber}} requires attention."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9404"))
                .andExpect(jsonPath("$.errors[0].field").value("templateVersion"));

        verifyNoInteractions(service);
    }

    @Test
    void createRequiresALocale() throws Exception {
        mvc.perform(post("/api/v1/notifications/templates")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "CLAIM_NOTICE_STANDARD",
                                  "templateVersion": 1,
                                  "bodyTemplate": "Claim {{claimNumber}} requires attention."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EIA-NTF-9404"))
                .andExpect(jsonPath("$.errors[0].field").value("locale"));

        verifyNoInteractions(service);
    }

    @Test
    void approvesATemplate() throws Exception {
        when(service.approve(TENANT, TEMPLATE_ID, APPROVER))
                .thenReturn(template("ACTIVE", APPROVER, UPDATED_AT));

        mvc.perform(post("/api/v1/notifications/templates/{id}/approve", TEMPLATE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvedBy": "a-adjuster"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.approvedBy").value(APPROVER))
                .andExpect(jsonPath("$.approvedAt").value("2026-10-21T18:00:00Z"));

        verify(service).approve(TENANT, TEMPLATE_ID, APPROVER);
    }

    @Test
    void approveRejectsAMissingApprover() throws Exception {
        mvc.perform(post("/api/v1/notifications/templates/{id}/approve", TEMPLATE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9404"))
                .andExpect(jsonPath("$.errors[0].field").value("approvedBy"));

        verify(service, never()).approve(any(), any(), any());
    }

    @Test
    void retiresATemplate() throws Exception {
        when(service.retire(TENANT, TEMPLATE_ID))
                .thenReturn(template("RETIRED", APPROVER, UPDATED_AT));

        mvc.perform(post("/api/v1/notifications/templates/{id}/retire", TEMPLATE_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));

        verify(service).retire(TENANT, TEMPLATE_ID);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/notifications/templates"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9501"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAMalformedTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/notifications/templates").header("X-EIA-Tenant", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9502"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesAnExpertBadRequestProblem() throws Exception {
        when(service.create(eq(TENANT), any(CreateNotificationTemplateCommand.class)))
                .thenThrow(new ExpertApiException(400, "EIA-NTF-9404", "Request validation failed",
                        "templateCode must not be blank", "VALIDATION", false));

        mvc.perform(post("/api/v1/notifications/templates")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "CLAIM_NOTICE_STANDARD",
                                  "templateVersion": 1,
                                  "locale": "en-US",
                                  "bodyTemplate": "Claim {{claimNumber}} requires attention."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9404"))
                .andExpect(jsonPath("$.detail").value("templateCode must not be blank"));
    }

    @Test
    void preservesAnExpertNotFoundProblem() throws Exception {
        when(service.approve(TENANT, TEMPLATE_ID, APPROVER))
                .thenThrow(new ExpertApiException(404, "EIA-NTF-9001", "Resource not found",
                        "Template was not found.", "NOT_FOUND", false));

        mvc.perform(post("/api/v1/notifications/templates/{id}/approve", TEMPLATE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvedBy": "a-adjuster"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9001"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesAnExpertConflictProblem() throws Exception {
        when(service.retire(TENANT, TEMPLATE_ID))
                .thenThrow(new ExpertApiException(409, "EIA-NTF-9207", "Invalid template status transition",
                        "Template is already RETIRED.", "CONFLICT", false));

        mvc.perform(post("/api/v1/notifications/templates/{id}/retire", TEMPLATE_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EIA-NTF-9207"))
                .andExpect(jsonPath("$.category").value("CONFLICT"));
    }

    private static NotificationTemplateDto template(String status, String approvedBy, Instant approvedAt) {
        return new NotificationTemplateDto(TEMPLATE_ID, "CLAIM_NOTICE_STANDARD", 1, "EMAIL",
                "en-US", BODY_TEMPLATE, status, approvedBy, approvedAt, CREATED_AT,
                approvedAt == null ? CREATED_AT : approvedAt);
    }
}
