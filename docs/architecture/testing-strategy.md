# Testing Strategy

## Testing Layers

| Layer | Scope | Tools |
|---|---|---|
| **Unit** | Individual services, validators, utilities | JUnit 5, Mockito, AssertJ |
| **Contract** | Documentation correctness and invariants | JUnit 5, AssertJ file assertions |
| **Round-trip fixtures** | Campaign package V2 serialization/deserialization | JSON fixture files, `@JsonTest` |
| **Playwright smoke** | Critical user journeys (core session loop) | Playwright via JUnit (headless browser) |

## Flyway Migrations

Database schema migrations (under `src/main/resources/db/migration/`):

| Migration | Purpose |
|---|---|
| V1 | Baseline schema from JPA entities |
| V2 | Remove class subclasses data |
| V3 | Add campaign package keys |
| V4 | Add campaign session and session scenes |
| V5 | Add structured adventure, quest, source annotations |
| V6 | Nullable link target IDs for catalog-scoped references |
| V7 | Add custom compendium ownership and provenance |
| V8 | Character sheet completion (attacks, features, inventory state, party live state) |
| V9 | Encounter map depth (waves, prep, rewards, combatant placement) |
| V10 | Add wave log types |
| V11 | Add party member XP |

## Asset Storage UUID Rule

Campaign package assets are stored under a stage directory named with `UUID.randomUUID().toString()` to guarantee isolation between concurrent import operations. Each import gets its own temp directory within the staging root, preventing file collisions and simplifying cleanup on discard.
