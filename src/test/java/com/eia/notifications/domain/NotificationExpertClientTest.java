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
 * Wire-level tests for the notification source proxy and the delivery-attempt
 * read: the optional flat source link on create, the optional source filter on
 * the list, and the tenant-scoped attempt history are forwarded unchanged, read
 * back on the public DTOs, and an expert 400 or 404 reaches the caller with the
 * same status.
 */
class NotificationExpertClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID NOTIFICATION_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final UUID ATTEMPT_ID = UUID.fromString("0192f3a4-0000-7000-8000-0000000000a1");
    private static final UUID RETRY_ID = UUID.fromString("0192f3a4-0000-7000-8000-0000000000a2");
    private static final UUID PARTY_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000002");
    private static final UUID SOURCE_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000003");
    private static final String EXPERT = "http://expert.test";
    private static final String LIST_URL = EXPERT + "/internal/v1/notifications";
    private static final String CREATE_URL = LIST_URL;
    private static final String ATTEMPTS_URL = LIST_URL + "/" + NOTIFICATION_ID + "/attempts";
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
    void forwardsSourceFilterToTheExpertAndReadsNotifications() {
        server.expect(requestTo(LIST_URL + "?tenantId=" + TENANT
                        + "&sourceType=DIRECT_CLAIM&sourceId=" + SOURCE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[" + notificationJson("DIRECT_CLAIM", SOURCE_ID, "CLM-2026-000000001") + "]",
                        MediaType.APPLICATION_JSON));

        List<NotificationDto> notifications = client.findAll(TENANT, "DIRECT_CLAIM", SOURCE_ID.toString());

        assertThat(notifications).singleElement().satisfies(notification -> {
            assertThat(notification.id()).isEqualTo(NOTIFICATION_ID);
            assertThat(notification.notificationNumber()).isEqualTo("NTF-2026-000000001");
            assertThat(notification.sourceType()).isEqualTo("DIRECT_CLAIM");
            assertThat(notification.sourceId()).isEqualTo(SOURCE_ID);
            assertThat(notification.sourceReference()).isEqualTo("CLM-2026-000000001");
        });
        server.verify();
    }

    @Test
    void forwardsAPartialSourceFilterSoTheExpertOwnsThePairRule() {
        server.expect(requestTo(LIST_URL + "?tenantId=" + TENANT + "&sourceType=DIRECT_CLAIM"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "https://errors.eia.example/eia-ntf-9412",
                                 "title": "Invalid source filter", "status": 400,
                                 "detail": "sourceId is required when sourceType is supplied.",
                                 "code": "EIA-NTF-9412", "category": "VALIDATION"}
                                """));

        assertThatThrownBy(() -> client.findAll(TENANT, "DIRECT_CLAIM", null))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(400);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9412");
                    assertThat(ex.title()).isEqualTo("Invalid source filter");
                    assertThat(ex.getMessage())
                            .isEqualTo("sourceId is required when sourceType is supplied.");
                    assertThat(ex.category()).isEqualTo("VALIDATION");
                    assertThat(ex.retryable()).isFalse();
                });
        server.verify();
    }

    @Test
    void preservesAnExpertUnknownSourceTypeProblemOnList() {
        server.expect(requestTo(LIST_URL + "?tenantId=" + TENANT
                        + "&sourceType=BRIBE&sourceId=" + SOURCE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "https://errors.eia.example/eia-ntf-9409",
                                 "title": "Invalid source type", "status": 400,
                                 "detail": "Source type 'BRIBE' is not one of: DIRECT_CLAIM, ASSUMED_CLAIM, POLICY, CONTRACT, PAYMENT, OTHER.",
                                 "code": "EIA-NTF-9409", "category": "VALIDATION"}
                                """));

        assertThatThrownBy(() -> client.findAll(TENANT, "BRIBE", SOURCE_ID.toString()))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(400);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9409");
                    assertThat(ex.title()).isEqualTo("Invalid source type");
                    assertThat(ex.category()).isEqualTo("VALIDATION");
                    assertThat(ex.retryable()).isFalse();
                });
        server.verify();
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

    @Test
    void readsProviderReferenceAndDeliveredAtFromTheExpertNotification() {
        server.expect(requestTo(LIST_URL + "/" + NOTIFICATION_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(notificationJson(null, null, null,
                        Instant.parse("2026-10-20T09:31:00Z"), "SIM-2026-000001"),
                        MediaType.APPLICATION_JSON));

        NotificationDto notification = client.findById(TENANT, NOTIFICATION_ID);

        assertThat(notification.deliveredAt()).isEqualTo(Instant.parse("2026-10-20T09:31:00Z"));
        assertThat(notification.providerReference()).isEqualTo("SIM-2026-000001");
        server.verify();
    }

    @Test
    void forwardsTheTenantAndReadsTheDeliveryAttemptHistory() {
        server.expect(requestTo(ATTEMPTS_URL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andRespond(withSuccess(attemptsJson(), MediaType.APPLICATION_JSON));

        List<NotificationDeliveryAttemptDto> attempts = client.findAttempts(TENANT, NOTIFICATION_ID);

        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0)).satisfies(attempt -> {
            assertThat(attempt.id()).isEqualTo(ATTEMPT_ID);
            assertThat(attempt.tenantId()).isEqualTo(TENANT);
            assertThat(attempt.notificationId()).isEqualTo(NOTIFICATION_ID);
            assertThat(attempt.attemptNumber()).isEqualTo(1);
            assertThat(attempt.channel()).isEqualTo("EMAIL");
            assertThat(attempt.provider()).isEqualTo("SIMULATOR");
            assertThat(attempt.providerReference()).isNull();
            assertThat(attempt.status()).isEqualTo("FAILED");
            assertThat(attempt.errorCode()).isEqualTo("SIMULATED_TIMEOUT");
            assertThat(attempt.errorMessage()).isEqualTo("Simulated provider timeout");
            assertThat(attempt.attemptedAt()).isEqualTo(Instant.parse("2026-10-22T11:59:00Z"));
        });
        assertThat(attempts.get(1)).satisfies(attempt -> {
            assertThat(attempt.id()).isEqualTo(RETRY_ID);
            assertThat(attempt.attemptNumber()).isEqualTo(2);
            assertThat(attempt.providerReference()).isEqualTo("SIM-2026-000001");
            assertThat(attempt.status()).isEqualTo("DELIVERED");
            assertThat(attempt.attemptedAt()).isEqualTo(Instant.parse("2026-10-22T12:00:00Z"));
        });
        server.verify();
    }

    @Test
    void preservesAnExpertNotFoundProblemOnAttempts() {
        server.expect(requestTo(ATTEMPTS_URL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "https://errors.eia.example/eia-ntf-9001",
                                 "title": "Resource not found", "status": 404,
                                 "detail": "Notification '0192f3a4-0000-7000-8000-000000000001' was not found.",
                                 "code": "EIA-NTF-9001", "category": "NOT_FOUND"}
                                """));

        assertThatThrownBy(() -> client.findAttempts(TENANT, NOTIFICATION_ID))
                .isInstanceOfSatisfying(ExpertApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(404);
                    assertThat(ex.code()).isEqualTo("EIA-NTF-9001");
                    assertThat(ex.title()).isEqualTo("Resource not found");
                    assertThat(ex.category()).isEqualTo("NOT_FOUND");
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
        return notificationJson(sourceType, sourceId, sourceReference, null, null);
    }

    private static String notificationJson(String sourceType, UUID sourceId, String sourceReference,
                                           Instant deliveredAt, String providerReference) {
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
                  "deliveredAt": %s,
                  "providerReference": %s,
                  "version": 0,
                  "createdAt": "2026-10-19T12:00:00Z",
                  "updatedAt": "2026-10-19T12:00:00Z",
                  "sourceType": %s,
                  "sourceId": %s,
                  "sourceReference": %s
                }
                """.formatted(
                jsonValue(deliveredAt == null ? null : deliveredAt.toString()),
                jsonValue(providerReference),
                jsonValue(sourceType),
                jsonValue(sourceId == null ? null : sourceId.toString()),
                jsonValue(sourceReference));
    }

    private static String attemptsJson() {
        return """
                [
                  {
                    "id": "0192f3a4-0000-7000-8000-0000000000a1",
                    "tenantId": "11111111-1111-7111-8111-111111111111",
                    "notificationId": "0192f3a4-0000-7000-8000-000000000001",
                    "attemptNumber": 1,
                    "channel": "EMAIL",
                    "provider": "SIMULATOR",
                    "providerReference": null,
                    "status": "FAILED",
                    "errorCode": "SIMULATED_TIMEOUT",
                    "errorMessage": "Simulated provider timeout",
                    "attemptedAt": "2026-10-22T11:59:00Z"
                  },
                  {
                    "id": "0192f3a4-0000-7000-8000-0000000000a2",
                    "tenantId": "11111111-1111-7111-8111-111111111111",
                    "notificationId": "0192f3a4-0000-7000-8000-000000000001",
                    "attemptNumber": 2,
                    "channel": "EMAIL",
                    "provider": "SIMULATOR",
                    "providerReference": "SIM-2026-000001",
                    "status": "DELIVERED",
                    "errorCode": null,
                    "errorMessage": null,
                    "attemptedAt": "2026-10-22T12:00:00Z"
                  }
                ]
                """;
    }

    private static String jsonValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
