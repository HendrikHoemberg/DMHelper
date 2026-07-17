# Modules and Ownership

All Java packages live under `dev.hendrikhoemberg.dmhelper.*`.

| Package | Responsibility |
|---|---|
| `adventure` | Structured adventures: chapters, scenes, transitions, checks, participants, scene links |
| `campaign` | Campaign CRUD, settings, package import/export (V1 and V2), package keys, catalog, validation |
| `calendar` | In-game calendar configuration, date advancement, timeline events |
| `common` | Shared config: PIN management, interceptors, exception handling, utilities |
| `config` | Application-level configuration (Spring Beans, WebMvc, CORS) |
| `dice` | Server-side dice rolling engine, roll history |
| `encounter` | Encounter CRUD, combat tracker, waves, combat log, difficulty calculator |
| `gamemap` | Map CRUD, map document storage, tokens, editor API, layer management |
| `handout` | Handout CRUD, file storage, presentation to player view |
| `ledger` | Append-only transaction ledger for gold and items |
| `library` | SRD compendium (statblocks, spells, conditions, rules, items, character options), custom content, search |
| `live` | Player-safe table state projection, WebSocket broadcasting, presentation management |
| `notes` | Wiki notes, quicknotes, wiki-link parsing, full-text search |
| `party` | Party roster, combat projection, live state (HP, temp HP, conditions) |
| `quest` | Quests, objectives with ALL/ANY completion mode, prerequisite DAG, session objective-change history |
| `session` | Campaign session lifecycle (start/resume/pause/end), workspace selection, session plan, scene visits |
| `sheet` | Character sheets (rules-aware derivation, class levels, spells, attacks, features, inventory, rest) |
| `treasury` | Item assignments, inventory states (EQUIPPED/CARRIED/STASHED/CONSUMED/LOST), attunement tracking |

Every module follows the `web/service/data` sub-package convention for controllers, services, and JPA entities/repositories.
