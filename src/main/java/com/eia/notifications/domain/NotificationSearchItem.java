package com.eia.notifications.domain;

import java.util.UUID;

/**
 * One row of the common global-search response for the Notifications vertical.
 * The shell renders {@code label}/{@code secondary} and navigates to
 * {@code href}; the fields are stable across all search-enabled services.
 */
public record NotificationSearchItem(
        UUID id,
        String type,
        String label,
        String secondary,
        String status,
        String href
) {}
