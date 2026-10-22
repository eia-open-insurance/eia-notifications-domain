package com.eia.notifications.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Domain-side adapter for the internal expert API. The domain owns validation
 * and state transitions; persistence is always delegated to the expert.
 */
@Component
public class NotificationExpertClient {

    private static final String TEMPLATES_PATH = "/api/v1/notifications/templates";
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public NotificationExpertClient(RestClient.Builder builder,
                                    @Value("${eia.notifications.expert-url:http://localhost:8099}") String expertUrl,
                                    ObjectMapper objectMapper) {
        this.client = builder.baseUrl(expertUrl).build();
        this.objectMapper = objectMapper;
    }

    public List<NotificationDto> findAll(UUID tenantId) {
        return call(() -> {
            List<NotificationDto> body = client.get()
                    .uri(uri -> uri.path("/internal/v1/notifications")
                            .queryParam("tenantId", tenantId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NotificationDto>>() {
                    });
            return body == null ? List.of() : body;
        });
    }

    /**
     * Tenant-scoped list, optionally narrowed to the notifications linked to
     * one source record. The expert evaluates the filter so the domain never
     * holds a second copy of the source-linkage rules; an invalid filter keeps
     * the expert's 400 problem for the caller.
     */
    public List<NotificationDto> findAll(UUID tenantId, String sourceType, String sourceId) {
        return call(() -> {
            List<NotificationDto> body = client.get()
                    .uri(uri -> {
                        var builder = uri.path("/internal/v1/notifications")
                                .queryParam("tenantId", tenantId);
                        if (sourceType != null) {
                            builder.queryParam("sourceType", sourceType);
                        }
                        if (sourceId != null) {
                            builder.queryParam("sourceId", sourceId);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NotificationDto>>() {
                    });
            return body == null ? List.of() : body;
        });
    }

    public NotificationDto findById(UUID tenantId, UUID id) {
        return call(() -> {
            NotificationDto body = client.get()
                    .uri(uri -> uri.path("/internal/v1/notifications/{id}")
                            .queryParam("tenantId", tenantId)
                            .build(id))
                    .retrieve()
                    .body(NotificationDto.class);
            return requireBody(body);
        });
    }

    /**
     * Forwards a create command, including the optional flat source link. The
     * expert owns the source-linkage rules; its 400 problem is translated with
     * the expert's status and code, so an invalid link stays a 400 for callers.
     */
    public NotificationDto create(UUID tenantId, CreateNotificationCommand command) {
        ExpertCreateRequest request = new ExpertCreateRequest(
                tenantId, command.notificationType(), command.channel(), command.recipientPartyId(),
                command.subject(), command.body(), command.templateCode(), command.templateVersion(),
                command.scheduledAt(), command.sourceType(), command.sourceId(), command.sourceReference());
        return call(() -> {
            NotificationDto body = client.post()
                    .uri("/internal/v1/notifications")
                    .body(request)
                    .retrieve()
                    .body(NotificationDto.class);
            return requireBody(body);
        });
    }

    public NotificationDto send(UUID tenantId, UUID id) {
        return call(() -> {
            NotificationDto body = client.post()
                    .uri(uri -> uri.path("/internal/v1/notifications/{id}/send")
                            .queryParam("tenantId", tenantId)
                            .build(id))
                    .retrieve()
                    .body(NotificationDto.class);
            return requireBody(body);
        });
    }

    public List<NotificationTemplateDto> findAllTemplates(UUID tenantId, NotificationTemplateStatus status,
                                                          String code) {
        return call(() -> {
            List<NotificationTemplateDto> body = client.get()
                    .uri(uri -> {
                        uri.path(TEMPLATES_PATH).queryParam("tenantId", tenantId);
                        if (status != null) {
                            uri.queryParam("status", status.name());
                        }
                        if (code != null && !code.isBlank()) {
                            uri.queryParam("code", code.trim());
                        }
                        return uri.build();
                    })
                    .header(TENANT_HEADER, tenantId.toString())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NotificationTemplateDto>>() {
                    });
            return body == null ? List.of() : body;
        });
    }

    public NotificationTemplateDto createTemplate(UUID tenantId, CreateNotificationTemplateCommand command) {
        ExpertCreateTemplateRequest request = new ExpertCreateTemplateRequest(
                tenantId,
                command.templateCode() == null ? null : command.templateCode().trim(),
                command.templateVersion(),
                trimToNull(command.channel()),
                command.locale() == null ? null : command.locale().trim(),
                command.bodyTemplate());
        return call(() -> {
            NotificationTemplateDto body = client.post()
                    .uri(TEMPLATES_PATH)
                    .header(TENANT_HEADER, tenantId.toString())
                    .body(request)
                    .retrieve()
                    .body(NotificationTemplateDto.class);
            return requireBody(body);
        });
    }

    public NotificationTemplateDto approveTemplate(UUID tenantId, UUID id, UUID approvedBy) {
        return call(() -> {
            NotificationTemplateDto body = client.post()
                    .uri(uri -> uri.path(TEMPLATES_PATH + "/{id}/approve")
                            .queryParam("tenantId", tenantId)
                            .build(id))
                    .header(TENANT_HEADER, tenantId.toString())
                    .body(new ApproveTemplateRequest(tenantId, approvedBy))
                    .retrieve()
                    .body(NotificationTemplateDto.class);
            return requireBody(body);
        });
    }

    public NotificationTemplateDto retireTemplate(UUID tenantId, UUID id) {
        return call(() -> {
            NotificationTemplateDto body = client.post()
                    .uri(uri -> uri.path(TEMPLATES_PATH + "/{id}/retire")
                            .queryParam("tenantId", tenantId)
                            .build(id))
                    .header(TENANT_HEADER, tenantId.toString())
                    .retrieve()
                    .body(NotificationTemplateDto.class);
            return requireBody(body);
        });
    }

    private <T> T call(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex.getMessage());
        }
    }

    private <T> T requireBody(T body) {
        if (body == null) {
            throw unavailable("the expert returned an empty response");
        }
        return body;
    }

    private ExpertApiException translate(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        try {
            JsonNode problem = objectMapper.readTree(ex.getResponseBodyAsString());
            if (problem != null && problem.hasNonNull("code")) {
                return new ExpertApiException(
                        status,
                        problem.path("code").asText(),
                        problem.path("title").asText("Notifications expert rejected the request"),
                        problem.path("detail").asText("Notifications expert returned HTTP " + status + "."),
                        problem.path("category").asText("DEPENDENCY"),
                        problem.path("retryable").asBoolean(status >= 500));
            }
        } catch (JsonProcessingException ignored) {
            // Fall through to the generic translation below.
        }
        return new ExpertApiException(status, "EIA-NTF-9601",
                "Notifications expert rejected the request",
                "Notifications expert returned HTTP " + status + ".", "DEPENDENCY", status >= 500);
    }

    private ExpertApiException unavailable(String reason) {
        return new ExpertApiException(502, "EIA-NTF-9601", "Notifications expert unavailable",
                "The Notifications expert service could not be reached: " + reason, "DEPENDENCY", true);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ExpertCreateRequest(
            UUID tenantId,
            String notificationType,
            String channel,
            UUID recipientPartyId,
            String subject,
            String body,
            String templateCode,
            Integer templateVersion,
            Instant scheduledAt,
            String sourceType,
            UUID sourceId,
            String sourceReference
    ) {
    }

    private record ExpertCreateTemplateRequest(
            UUID tenantId,
            String templateCode,
            Integer templateVersion,
            String channel,
            String locale,
            String bodyTemplate
    ) {
    }

    private record ApproveTemplateRequest(UUID tenantId, UUID approvedBy) {
    }
}
