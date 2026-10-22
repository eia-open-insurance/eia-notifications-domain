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
 * Public notification template administration API. Tenant context is always
 * taken from the signed X-EIA-Tenant header (SPEC 12.6), never from the body.
 */
@RestController
@RequestMapping("/api/v1/notifications/templates")
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    public NotificationTemplateController(NotificationTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationTemplateDto> list(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                              @RequestParam(name = "status", required = false) String status,
                                              @RequestParam(name = "code", required = false) String code) {
        return service.list(tenantId, status, code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationTemplateDto create(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                          @Valid @RequestBody CreateNotificationTemplateCommand command) {
        return service.create(tenantId, command);
    }

    @PostMapping("/{id}/approve")
    public NotificationTemplateDto approve(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody ApproveNotificationTemplateCommand command) {
        return service.approve(tenantId, id, command.approvedBy());
    }

    @PostMapping("/{id}/retire")
    public NotificationTemplateDto retire(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                          @PathVariable UUID id) {
        return service.retire(tenantId, id);
    }
}
