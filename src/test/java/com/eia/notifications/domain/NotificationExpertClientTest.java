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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Wire-level tests for the notification create proxy: the optional flat source
 * link is forwarded unchanged, read back on the public DTO, and an expert 400
 * for an invalid link reaches the caller as a 400.
 */
class NotificationExpertClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID NOTIFICATION_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final UUID PARTY_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000002");
    private static final UUID SOURCE_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000003");
    private static final String EXPERT = "http://expert.test";
    private static final String CREATE_URL = EXPERT + "/internal/v1/notifications";
    private static final Instant CREATED_AT = Instant.parse("2026-10-19T12:00:00Z");

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
    void createsWithSourceLinkageAndReadsItBack() {
        server.expect(requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "tenantId": "11111111-1111-7111-8111-111111111111",
                          "notificationType": "NEW_CLAIM_NOTICE",
                          "channel": "EMAIL",
                          "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                          "subject": "Claim requires attention",
                          "body": "Claim CLM-2026-000000001 requires attention.",
                          "templateCode": "CLAIM_NOTICE_STANDARD",
                          "templateVersion": 1,
                          "scheduledAt": null,
                          "sourceType": "DIRECT_CLAIM",
                          "sourceId": "0192f3a4-0000-7000-8000-000000000003",
                          "sourceReference": "CLM-2026-000000001"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(notificationJson("DIRECT_CLAIM", SOURCE_ID, "CLM-2026-000000001"),
                        MediaType.APPLICATION_JSON));

        NotificationDto notification = client.create(TENANT,
                command("DIRECT_CLAIM", SOURCE_ID, "CLM-2026-000000001"));

        assertThat(notification.id()).isEqualTo(NOTIFICATION_ID);
        assertThat(notification.notificationNumber()).isEqualTo("NTF-2026-000000001");
        assertThat(notification.sourceType()).isEqualTo("DIRECT_CLAIM");
        assertThat(notification.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(notification.sourceReference()).isEqualTo("CLM-2026-000000001");
        server.verify();
    }

    @Test
    void createsWithoutSourceLinkageAndReadsNulls() {
        server.expect(requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "tenantId": "11111111-1111-7111-8111-111111111111",
                          "notificationType": "NEW_CLAIM_NOTICE",
                          "channel": "EMAIL",
                          "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                          "subject": "Claim requires attention",
                          "body": "Claim CLM-2026-000000001 requires attention.",
                          "templateCode": "CLAIM_NOTICE_STANDARD",
                          "templateVersion": 1,
                          "scheduledAt": null,
                          "sourceType": null,
                          "sourceId": null,
                          "sourceReference": null
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(notificationJson(null, null, null), MediaType.APPLICATION_JSON));

        NotificationDto notification = client.create(TENANT, command(null, null, null));

        assertThat(notification.sourceType()).isNull();
        assertThat(notification.sourceId()).isNull();
        assertThat(notification.sourceReference()).isNull();
        server.verify();
    }

    @Test
    void preservesAnExpertBadRequestProblemOnCreate() {
        server.expect(requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "https://errors.eia.example/eia-ntf-9404",
                                 "title": "Request validation failed", "status": 400,
                                 "detail": "sourceId is required when sourceType is supplied.",
                                 "code": "EIA-NTF-9404", "category": "VALIDATION"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, command("DIRECT_CLAIM", null, null)))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(400);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9404");
                    assertThat(ex.title()).isEqualTo("Request validation failed");
                    assertThat(ex.getMessage())
                            .isEqualTo("sourceId is required when sourceType is supplied.");
                    assertThat(ex.category()).isEqualTo("VALIDATION");
                    assertThat(ex.retryable()).isFalse();
                });
        server.verify();
    }

    private static CreateNotificationCommand command(String sourceType, UUID sourceId, String sourceReference) {
        return new CreateNotificationCommand("NEW_CLAIM_NOTICE", "EMAIL", PARTY_ID,
                "Claim requires attention", "Claim CLM-2026-000000001 requires attention.",
                "CLAIM_NOTICE_STANDARD", 1, null, sourceType, sourceId, sourceReference);
    }

    private static String notificationJson(String sourceType, UUID sourceId, String sourceReference) {
        return """
                {
                  "id": "0192f3a4-0000-7000-8000-000000000001",
                  "tenantId": "11111111-1111-7111-8111-111111111111",
                  "notificationNumber": "NTF-2026-000000001",
                  "notificationType": "NEW_CLAIM_NOTICE",
                  "channel": "EMAIL",
                  "status": "CREATED",
                  "recipientPartyId": "0192f3a4-0000-7000-8000-000000000002",
                  "subject": "Claim requires attention",
                  "body": "Claim CLM-2026-000000001 requires attention.",
                  "templateCode": "CLAIM_NOTICE_STANDARD",
                  "templateVersion": 1,
                  "scheduledAt": null,
                  "sentAt": null,
                  "version": 0,
                  "createdAt": "2026-10-19T12:00:00Z",
                  "updatedAt": "2026-10-19T12:00:00Z",
                  "sourceType": %s,
                  "sourceId": %s,
                  "sourceReference": %s
                }
                """.formatted(jsonValue(sourceType),
                jsonValue(sourceId == null ? null : sourceId.toString()),
                jsonValue(sourceReference));
    }

    private static String jsonValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
