# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Repository baseline.
- Delivery-attempt history proxy: `GET /api/v1/notifications/{id}/attempts` forwards the tenant to
  the expert and preserves its `404` problem; the public `NotificationDto` exposes the delivery
  evidence `providerReference` and `deliveredAt`.
- Notification source linkage: create accepts optional `sourceType`, `sourceId`, and `sourceReference`;
  the public `NotificationDto` exposes them; the domain forwards the link unchanged and preserves the
  expert's `400` problem for an invalid link.
- Notification source filter: `GET /api/v1/notifications` accepts an optional `sourceType` + `sourceId`
  pair and forwards it to the expert; an incomplete or unknown filter preserves the expert's `400` problem.
- Notification template management proxy: tenant-scoped list with optional `status`/`code` filters,
  create (`DRAFT`), approve with `{approvedBy}`, and retire, preserving expert `400`/`404`/`409` problems.
- MockRestServiceServer wire tests for the template proxy and MockMvc API tests for the public routes.
