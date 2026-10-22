package com.eia.notifications.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Wire-level tests for the notification template proxy: exact expert URLs,
 * tenant forwarding, request payloads, response decoding, and preservation of
 * the expert's 400/404/409 problems.
 */
class NotificationTemplateExpertClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID TEMPLATE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPROVER = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final String EXPERT = "http://expert.test";
    private static final String TEMPLATES_URL = EXPERT + "/api/v1/notifications/templates";
    private static final Instant CREATED_AT = Instant.parse("2026-10-19T12:00:00Z");
    private static final Instant APPROVED_AT = Instant.parse("2026-10-21T18:00:00Z");

    private static final String TEMPLATE_JSON = """
            {
              "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
              "code": "CLAIM_NOTICE_STANDARD",
              "version": 1,
              "channel": "EMAIL",
              "locale": "en-US",
              "bodyTemplate": "Claim {{claimNumber}} requires attention.",
              "status": "DRAFT",
              "approvedBy": null,
              "approvedAt": null,
              "createdAt": "2026-10-19T12:00:00Z",
              "updatedAt": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String ACTIVE_TEMPLATE_JSON = """
            {
              "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
              "code": "CLAIM_NOTICE_STANDARD",
              "version": 1,
              "channel": "EMAIL",
              "locale": "en-US",
              "bodyTemplate": "Claim {{claimNumber}} requires attention.",
              "status": "ACTIVE",
              "approvedBy": "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
              "approvedAt": "2026-10-21T18:00:00Z",
              "createdAt": "2026-10-19T12:00:00Z",
              "updatedAt": "2026-10-21T18:00:00Z"
            }
            """;

    private MockRestServiceServer server;
    private NotificationExpertClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .messageConverters(converters -> {
                    ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                            .build();
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
                });
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NotificationExpertClient(builder, EXPERT, new ObjectMapper());
    }

    @Test
    void listsTemplatesWithStatusAndCodeFilters() {
        server.expect(requestTo(TEMPLATES_URL + "?tenantId=" + TENANT
                        + "&status=DRAFT&code=CLAIM_NOTICE_STANDARD"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andRespond(withSuccess("[" + TEMPLATE_JSON + "]", MediaType.APPLICATION_JSON));

        List<NotificationTemplateDto> templates = client.findAllTemplates(
                TENANT, NotificationTemplateStatus.DRAFT, "CLAIM_NOTICE_STANDARD");

        assertThat(templates).hasSize(1);
        NotificationTemplateDto template = templates.getFirst();
        assertThat(template.id()).isEqualTo(TEMPLATE_ID);
        assertThat(template.code()).isEqualTo("CLAIM_NOTICE_STANDARD");
        assertThat(template.version()).isEqualTo(1);
        assertThat(template.channel()).isEqualTo("EMAIL");
        assertThat(template.locale()).isEqualTo("en-US");
        assertThat(template.bodyTemplate()).isEqualTo("Claim {{claimNumber}} requires attention.");
        assertThat(template.status()).isEqualTo("DRAFT");
        assertThat(template.createdAt()).isEqualTo(CREATED_AT);
        server.verify();
    }

    @Test
    void listsTemplatesWithOnlyTheTenantWhenFiltersAreAbsent() {
        server.expect(requestTo(TEMPLATES_URL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.findAllTemplates(TENANT, null, " ")).isEmpty();
        server.verify();
    }

    @Test
    void createsATemplateWithTenantAndTrimmedFields() {
        server.expect(requestTo(TEMPLATES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(content().json("""
                        {
                          "tenantId": "11111111-1111-7111-8111-111111111111",
                          "templateCode": "CLAIM_NOTICE_STANDARD",
                          "templateVersion": 1,
                          "channel": "EMAIL",
                          "locale": "en-US",
                          "bodyTemplate": "Claim {{claimNumber}} requires attention."
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(TEMPLATE_JSON, MediaType.APPLICATION_JSON));

        NotificationTemplateDto template = client.createTemplate(TENANT,
                new CreateNotificationTemplateCommand(" CLAIM_NOTICE_STANDARD ", 1, " EMAIL ", "en-US",
                        "Claim {{claimNumber}} requires attention."));

        assertThat(template.id()).isEqualTo(TEMPLATE_ID);
        assertThat(template.status()).isEqualTo("DRAFT");
        server.verify();
    }

    @Test
    void createsATemplateWithoutAChannel() {
        server.expect(requestTo(TEMPLATES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "tenantId": "11111111-1111-7111-8111-111111111111",
                          "templateCode": "CLAIM_NOTICE_STANDARD",
                          "templateVersion": 2,
                          "channel": null,
                          "locale": "en-GB",
                          "bodyTemplate": "Claim {{claimNumber}} requires attention."
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(TEMPLATE_JSON, MediaType.APPLICATION_JSON));

        client.createTemplate(TENANT, new CreateNotificationTemplateCommand("CLAIM_NOTICE_STANDARD", 2,
                null, "en-GB", "Claim {{claimNumber}} requires attention."));

        server.verify();
    }

    @Test
    void approvesATemplateWithTheApproverPayload() {
        server.expect(requestTo(TEMPLATES_URL + "/" + TEMPLATE_ID + "/approve?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(content().json("""
                        {
                          "tenantId": "11111111-1111-7111-8111-111111111111",
                          "approvedBy": "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(ACTIVE_TEMPLATE_JSON, MediaType.APPLICATION_JSON));

        NotificationTemplateDto template = client.approveTemplate(TENANT, TEMPLATE_ID, APPROVER);

        assertThat(template.status()).isEqualTo("ACTIVE");
        assertThat(template.approvedBy()).isEqualTo(APPROVER);
        assertThat(template.approvedAt()).isEqualTo(APPROVED_AT);
        server.verify();
    }

    @Test
    void retiresATemplate() {
        server.expect(requestTo(TEMPLATES_URL + "/" + TEMPLATE_ID + "/retire?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andRespond(withSuccess(ACTIVE_TEMPLATE_JSON.replace("\"ACTIVE\"", "\"RETIRED\""),
                        MediaType.APPLICATION_JSON));

        NotificationTemplateDto template = client.retireTemplate(TENANT, TEMPLATE_ID);

        assertThat(template.status()).isEqualTo("RETIRED");
        server.verify();
    }

    @Test
    void preservesAnExpertBadRequestProblemOnCreate() {
        server.expect(requestTo(TEMPLATES_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "https://errors.eia.example/eia-ntf-9404",
                                 "title": "Request validation failed", "status": 400,
                                 "detail": "templateCode must not be blank",
                                 "code": "EIA-NTF-9404", "category": "VALIDATION"}
                                """));

        assertThatThrownBy(() -> client.createTemplate(TENANT,
                new CreateNotificationTemplateCommand("", 1, "EMAIL", "en-US", "Body")))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(400);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9404");
                    assertThat(ex.title()).isEqualTo("Request validation failed");
                    assertThat(ex.getMessage()).isEqualTo("templateCode must not be blank");
                    assertThat(ex.category()).isEqualTo("VALIDATION");
                    assertThat(ex.retryable()).isFalse();
                });
        server.verify();
    }

    @Test
    void preservesAnExpertNotFoundProblemOnApprove() {
        server.expect(requestTo(TEMPLATES_URL + "/" + TEMPLATE_ID + "/approve?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "https://errors.eia.example/eia-ntf-9001",
                                 "title": "Resource not found", "status": 404,
                                 "detail": "Template aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa was not found.",
                                 "code": "EIA-NTF-9001", "category": "NOT_FOUND"}
                                """));

        assertThatThrownBy(() -> client.approveTemplate(TENANT, TEMPLATE_ID, APPROVER))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(404);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9001");
                    assertThat(ex.category()).isEqualTo("NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void preservesAnExpertConflictProblemOnRetire() {
        server.expect(requestTo(TEMPLATES_URL + "/" + TEMPLATE_ID + "/retire?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "https://errors.eia.example/eia-ntf-9207",
                                 "title": "Invalid template status transition", "status": 409,
                                 "detail": "Template is already RETIRED.",
                                 "code": "EIA-NTF-9207", "category": "CONFLICT"}
                                """));

        assertThatThrownBy(() -> client.retireTemplate(TENANT, TEMPLATE_ID))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(409);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9207");
                    assertThat(ex.category()).isEqualTo("CONFLICT");
                });
        server.verify();
    }

    @Test
    void mapsAnEmptyTemplateResponseToAnUnavailableExpert() {
        server.expect(requestTo(TEMPLATES_URL + "/" + TEMPLATE_ID + "/retire?tenantId=" + TENANT))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.retireTemplate(TENANT, TEMPLATE_ID))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(502);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9601");
                });
        server.verify();
    }
}
