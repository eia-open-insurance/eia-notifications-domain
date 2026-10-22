# eia-notifications-domain

- GitHub organization: `eia-open-insurance`
- Repository URL: https://github.com/eia-open-insurance/eia-notifications-domain
- Type: Spring Boot service
- Deployable: Yes
- Owner: Notifications
- Runtime: Java 22 and Spring Boot
- Data store: eia_notifications (tx schema)
- Depends on: eia-notifications-expert, eia-contracts, eia-platform/libs

## Purpose

Notifications business rules, transaction catalog, orchestration, approvals, and APIs. Notification rules, templates, routing, delivery, escalation, and acknowledgement.

## Responsibilities

- Keep this repository focused on one deployable component or one platform concern.
- Follow the repository standard in `../CONVENTIONS.md` and the specification in `../../SPEC.md`.
- Publish and consume contracts through `eia-contracts`.
- Maintain README, CODEOWNERS, SECURITY, CHANGELOG, tests, and local verification.
- Contain no business logic in shared libraries or front-end remotes.

## Verification

- Lint and format
- Unit tests with 100% business-logic coverage where applicable
- Contract tests
- Integration or Playwright tests where applicable
- Security scan and SBOM
- Build and publish

## Notes

Owns the business logic for this vertical.

## Implemented first slice

- Spring Boot 3.4.0, Java 22, port **8100**.
- Public API under `/api/v1/notifications`; `X-EIA-Tenant` is required on every request.
- `GET` collection, `GET /{id}`, `POST` create, and `POST /{id}/send`.
- Create accepts an optional flat source link (`sourceType`, `sourceId`, `sourceReference`) and forwards
  it to the expert unchanged. The expert owns the source-linkage rules; its `400` problem is propagated unchanged.
- `GET` collection accepts an optional `sourceType` + `sourceId` query filter and forwards it to the
  expert. The expert owns the pair rule and source-type set; an invalid filter's `400` problem is propagated unchanged.
- Template administration: `GET /templates` (optional `status` and `code` filters), `POST /templates`,
  `POST /templates/{id}/approve` with `{approvedBy}`, and `POST /templates/{id}/retire`.
  Templates are created as `DRAFT`, become `ACTIVE` only through approval (NTF-14), and may be retired.
  Expert `400`/`404`/`409` problems are propagated unchanged.
- Owns request validation, SPEC 11.8.5 channel/type checks, send-state checks, and `sent_at >= scheduled_at`; calls the expert through a `RestClient`.
- Expert failures are propagated as RFC 9457 `application/problem+json` with the expert's status and `EIA-NTF-*` code.

## Local verification

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-22.0.2.9-hotspot'
.\gradlew build
java -jar build/libs/eia-notifications-domain-0.1.0-SNAPSHOT.jar
```

Start `eia-notifications-expert` on port 8099 first. The expert URL is configured by
`eia.notifications.expert-url` and defaults to `http://localhost:8099`.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
