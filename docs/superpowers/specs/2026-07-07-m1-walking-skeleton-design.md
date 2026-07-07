# M1: Walking Skeleton — Design

**Date:** 2026-07-07 · **Status:** approved · **Parent:** [SPEC.md](../../../SPEC.md)

## Goal

`java -jar dmhelper.jar` → create, export, and import a campaign in the browser. Every architectural
decision from the spec is locked in from M1 — nothing is temporary scaffolding.

## 1. Project Cleanup

Remove from `pom.xml`:
- `spring-boot-starter-security` — spec uses PIN-based access, not Spring Security
- `thymeleaf-extras-springsecurity6` — unused without Spring Security
- `spring-boot-docker-compose` — app is self-contained with H2 file mode

Delete:
- `compose.yaml` — no Docker services needed

Add to `pom.xml`:
- `flyway-core` + `flyway-database-h2` — schema migrations from day one (spec §2.1, §3)

Java version: **25** (LTS). Spec updated to match.

## 2. Package Structure

Feature-module layout from day one (spec §2.2):

```
dev.hendrikhoemberg.dmhelper
├── DmhelperApplication.java
├── campaign
│   ├── web          → CampaignController (Thymeleaf views + REST export/import)
│   ├── service      → CampaignService (CRUD + JSON export/import)
│   └── data         → Campaign entity + CampaignRepository
├── common
│   └── web          → fragment controller helpers (sidebar, empty state, etc.)
└── config           → WebMvcConfig, JacksonConfig
```

No other feature modules exist yet. Each module follows `web/service/data` sub-packages.

## 3. Flyway Migration V1

**File:** `src/main/resources/db/migration/V1__create_campaign.sql`

```sql
CREATE TABLE campaign (
    id          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description CLOB,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settings    CLOB
);
```

- `ddl-auto` set to `validate` — Flyway owns the schema
- No other tables until M2

## 4. Data Layer

### Campaign Entity

JPA entity mapped to `campaign` table. Fields: `id` (UUID, generated), `name` (String, not null),
`description` (String/CLOB), `createdAt` (Instant), `settings` (String/CLOB, raw JSON).

Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Entity`.

### CampaignRepository

`JpaRepository<Campaign, UUID>`. One custom query: `findAllByOrderByNameAsc()`.

### CampaignService

- `create(name, description)` — persist and return
- `findAll()` — all campaigns, ordered by name
- `findById(UUID)` — single lookup, throw if not found
- `update(UUID, name, description)` — load, update fields, save
- `delete(UUID)` — hard delete (no cascading in M1)
- `exportToJson(UUID)` — build the full `dmcampaign.json` structure (formatVersion 1, campaign object, empty sub-arrays for party/maps/encounters/notes/statBlocks/handouts)
- `importFromJson(String json)` — parse, validate formatVersion, extract campaign object, create new Campaign (IDs are always fresh; never overwrite). Return the new Campaign.

## 5. Web Layer

### CampaignController

All routes return Thymeleaf HTML fragments or JSON:

| Route | Method | Returns | Notes |
|---|---|---|---|
| `/` | GET | Redirect to `/campaigns` | |
| `/campaigns` | GET | HTML (page with list) | Full page on first load; htmx fragment on subsequent |
| `/campaigns` | POST | HTML fragment (new card) | Form submit, returns list item fragment |
| `/campaigns/{id}` | GET | HTML (detail page) | |
| `/campaigns/{id}` | PUT | HTML fragment (updated card) | Inline edit |
| `/campaigns/{id}` | DELETE | HTTP 200, HX-Redirect header | htmx redirects to list |
| `/campaigns/{id}/export` | GET | `application/json` download | Content-Disposition: attachment |
| `/campaigns/import` | POST | HTML fragment (new card) | Multipart JSON file upload |

### Base Layout

`templates/layout.html` — Thymeleaf fragment with:
- `<nav>`: app title "DMHelper", DM Mode toggle (checkbox, non-functional, styled), PIN display (monospace span, shows placeholder `—`)
- `<main th:replace="~{::content}">` — content slot
- CSS and JS includes in `<head>`: `app.css`, `htmx.min.js`, `alpine.min.js` (Konva loaded lazily on map pages, not in base layout — but committed now)

### HTML Fragments

- `campaigns/list.html` — card grid, each card linked to detail; "New Campaign" button triggers inline form
- `campaigns/card.html` — single campaign card fragment (reused after create/update)
- `campaigns/form.html` — inline create/edit form (name + description inputs, save/cancel)
- `campaigns/detail.html` — campaign detail view (name, description, date; edit/delete/export/import buttons)
- `common/sidebar.html` — sidebar shell (empty in M1, used by later milestones)
- `common/empty-state.html` — "No campaigns yet" placeholder

### Design System

Single CSS file (`static/css/app.css`):
- CSS custom properties: `--color-bg`, `--color-surface`, `--color-surface-hover`, `--color-text`, `--color-text-muted`, `--color-accent`, `--color-accent-hover`, `--color-danger`, `--color-success`, `--color-warning`, `--color-border`
- Spacing scale: `--space-xs` (4px) through `--space-xl` (32px)
- Type scale: `--text-sm` (0.875rem), `--text-base` (1rem), `--text-lg` (1.25rem), `--text-xl` (1.5rem), `--text-2xl` (2rem)
- Dark theme as default (low-light game room)
- Modern CSS: grid, flexbox, `dialog` element, transitions
- Component styles: `.card`, `.btn`, `.btn-primary`, `.btn-danger`, `.btn-ghost`, `.form-group`, `.modal-overlay`, `.badge`, `.navbar`
- DM Mode toggle visual: colored border on `<body>` when toggled (`.dm-mode-off` class)

Theme applied via `<body>` with `data-theme="dark"`. DM mode off adds `dm-mode-off` class.

## 6. Frontend (Vendored Assets)

All three libraries committed to `src/main/resources/static/vendor/`:
- `htmx.min.js` (v2.x — htmx v2, the current major)
- `alpine.min.js` (v3.x)
- `konva.min.js` (v9.x — full UMD, self-contained)

`VENDOR.md` (Markdown table):

| File | Version | Upstream URL | SHA-256 |
|---|---|---|---|
| `vendor/htmx.min.js` | x.y.z | https://unpkg.com/htmx.org@... | `abc...` |
| `vendor/alpine.min.js` | x.y.z | https://unpkg.com/alpinejs@... | `def...` |
| `vendor/konva.min.js` | x.y.z | https://unpkg.com/konva@... | `ghi...` |

No CDN references. All JS loaded from local `static/vendor/`.

## 7. JSON Export/Import Format (v1)

The format is the real format from day one. Empty campaigns export all sub-arrays empty:

```json
{
  "formatVersion": 1,
  "campaign": {
    "name": "Curse of the Amber Court",
    "description": "A campaign set in..."
  },
  "party": [],
  "statBlocks": [],
  "handouts": [],
  "maps": [],
  "encounters": [],
  "notes": []
}
```

**Export:** `CampaignService.exportToJson()` — Jackson `ObjectMapper` serializes a DTO.

**Import:** `CampaignService.importFromJson()` — parse JSON, validate `formatVersion` == 1, extract campaign name/description, create new Campaign. Ignore empty sub-arrays. Throw descriptive `IllegalArgumentException` for invalid input; controller catches and returns a problem+json error.

## 8. Configuration

### application.properties

```properties
# Data source
spring.datasource.url=jdbc:h2:file:${user.home}/.dmhelper/data/dmhelper
spring.datasource.driver-class-name=org.h2.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

# H2
spring.h2.console.enabled=false

# Thymeleaf
spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html

# Server
server.port=8080
```

### WebMvcConfig

No special configuration needed beyond Boot defaults. Static resources served from `classpath:/static/`.

### JacksonConfig

Configure `ObjectMapper` for:
- `SerializationFeature.INDENT_OUTPUT` — readable export JSON
- `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` — strict import validation
- Java 25 `Instant` module (auto-registered by Boot)

## 9. Testing

### CampaignServiceTest (unit + integration)

- `shouldCreateCampaign()` — create returns persisted entity with ID
- `shouldFindAllOrderedByName()` — insert multiple, verify order
- `shouldUpdateCampaign()` — update name/description, verify
- `shouldDeleteCampaign()` — delete, verify not found
- `shouldExportEmptyCampaignToJson()` — export has formatVersion 1, correct campaign fields, all sub-arrays present and empty
- `shouldImportJsonToNewCampaign()` — import valid JSON, returns new Campaign with fresh ID
- `shouldRoundTripCampaign()` — export → import → deep equality (not same ID, but same name/description)
- `shouldRejectInvalidFormatVersion()` — import with formatVersion 99 throws
- `shouldRejectMalformedJson()` — import garbage throws

### CampaignControllerTest (webmvc-test)

- `shouldRenderCampaignList()` — GET /campaigns returns 200, contains campaign names
- `shouldRenderEmptyList()` — GET /campaigns with no data shows empty-state
- `shouldCreateCampaign()` — POST /campaigns returns fragment with new campaign name
- `shouldRenderDetail()` — GET /campaigns/{id} returns page with campaign name
- `shouldUpdateCampaign()` — PUT returns fragment with updated name
- `shouldDeleteCampaign()` — DELETE returns redirect header
- `shouldExportCampaign()` — GET export returns JSON content-type and attachment header
- `shouldImportCampaign()` — POST import returns fragment for new campaign

All tests use `@WebMvcTest` + `@MockBean` for the service. No database in controller tests.

## 10. Build & Run

```bash
./mvnw clean package
java -jar target/dmhelper-0.0.1-SNAPSHOT.jar
# Opens browser at http://localhost:8080
```

No browser auto-open in M1 (add later). Dev loop: `./mvnw spring-boot:run` with DevTools for hot reload.

## 11. What M1 Explicitly Does NOT Include

- Statblock library, party roster, maps, encounters, combat tracker, notes, handouts, player view — all M2+
- DM Mode actual functionality (toggle is present in UI, inert)
- PIN gate (PIN display is present, shows `—`)
- WebSocket (`spring-boot-starter-websocket` not added until M6)
- commonmark-java (not added until M7)
- Backup rotation (M8)
- SRD seed data (M2)
- JSON Schema endpoints for generative tooling (M8)
- Playwright smoke tests (M8)
