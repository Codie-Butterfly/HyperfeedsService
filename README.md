# Hyperfeeds Service

Backend for the Hyperfeeds customer and employee mobile application.

## Technology

- Java 17
- Spring Boot 4
- PostgreSQL 17
- Flyway database migrations
- Maven

The service starts as a modular monolith. Its first modules are identity, branches,
catalogue, pricing, and inventory.

## Local setup

Requirements: Java 17 and Docker.

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

The API is served below `/api`. Health is available at
`http://localhost:8080/api/actuator/health` and Swagger UI at
`http://localhost:8080/api/swagger-ui.html`.

Local database defaults are intentionally development-only and can be replaced with
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` environment variables.

## Catalogue CSV import

Administrators can upload a UTF-8 CSV file to `POST /api/catalogue/import` as a
multipart field named `file`. Required columns are `sku`, `name`, `category`, and
`pack_size`. Optional columns are `barcode`, `description`, `image_url`, `published`,
and `active`. Existing products are updated by SKU and the whole import rolls back
if any row is invalid.

## Production configuration

The `prod` profile requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`TOKEN_PEPPER`, `OTP_PEPPER`, `INFOBIP_BASE_URL`, `INFOBIP_API_KEY`,
`INFOBIP_SMS_SENDER`, `PAYNOW_INTEGRATION_ID`, `PAYNOW_INTEGRATION_KEY`, and
`PAYNOW_RESULT_URL`. Secrets have no production defaults and must be supplied by
the deployment environment. Do not reuse credentials stored in older services.

Production customer verification codes are delivered through Infobip SMS. Local
development defaults to the log-only OTP sender; set `OTP_PROVIDER=infobip` to test
the real provider locally.

Paynow checkout follows the ImbaService V2 mobile-money flow: EcoCash, OneMoney, or
Telecash sends a handset authorization prompt. Hyperfeeds polls Paynow every 30
seconds and times out after 30 minutes. Payment outcomes are delivered through the
in-app notification inbox; Hyperfeeds does not send notification SMS messages.

Database integration tests are opt-in so unit tests do not depend on PostgreSQL:

```bash
docker compose up -d postgres
RUN_DB_TESTS=true ./mvnw test
```

## Delivery tracker

| ID | Work item | Status |
|---|---|---|
| HF-001 | Project scaffold and local PostgreSQL | Done |
| HF-002 | Initial users, roles, branches, catalogue and inventory schema | Done |
| HF-003 | Customer phone signup and OTP verification | Done |
| HF-004 | Employee authentication and access/refresh tokens | Done |
| HF-005 | Role and branch-based authorization | Done |
| HF-006 | Branch management APIs | Done |
| HF-007 | Catalogue, pricing and inventory APIs | Done |
| HF-008 | Catalogue bulk import | Done |
| HF-009 | Chick availability and booking | Done |
| HF-010 | Cart, orders and Paynow | Done |
| HF-011 | Announcements and specials | Done |
| HF-012 | Notifications | Done |
| HF-013 | Livestock questions and guarded AI assistance | Done |
