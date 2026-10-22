package com.eia.notifications.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public Notifications API. Tenant context is always taken from the signed
 * X-EIA-Tenant header (SPEC 12.6), never from the request body.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationDto> list(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                      @RequestParam(name = "sourceType", required = false) String sourceType,
                                      @RequestParam(name = "sourceId", required = false) String sourceId) {
        return service.list(tenantId, sourceType, sourceId);
    }

    @GetMapping("/search")
    public NotificationSearchResponse search(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                             @RequestParam(name = "q", required = false) String q,
                                             @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return service.search(tenantId, q, limit);
    }

    @GetMapping("/{id}")
    public NotificationDto get(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.get(tenantId, id);
    }

    @GetMapping("/{id}/attempts")
    public List<NotificationDeliveryAttemptDto> attempts(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                         @PathVariable UUID id) {
        return service.attempts(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDto create(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                  @Valid @RequestBody CreateNotificationCommand command) {
        return service.create(tenantId, command);
    }

    @PostMapping("/{id}/send")
    public NotificationDto send(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.send(tenantId, id);
    }
}
