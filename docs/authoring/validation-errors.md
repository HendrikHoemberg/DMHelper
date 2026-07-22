# Validation Error Catalog

The JSON/API is authoritative. If this Markdown lags, consult `GET /api/v1/validation-errors`
or `src/main/resources/agent/validation-error-catalog.json`.

| Code | Severity | Summary |
|------|----------|---------|
| `AMBIGUOUS_REFERENCE` | WARNING | A reference matches more than one target. |
| `ASSET_DIGEST_MISMATCH` | ERROR | The computed digest of an asset does not match the declared digest. |
| `ASSET_EXTENSION_MISMATCH` | ERROR | The file extension of an asset does not match its declared media type. |
| `ASSET_NOT_FOUND` | ERROR | A referenced asset file could not be found in the package. |
| `ASSET_PATH_INVALID` | ERROR | An asset path contains illegal characters or structure. |
| `ASSET_READ_ERROR` | ERROR | An asset file could not be read. |
| `ASSET_SIGNATURE_MISMATCH` | ERROR | The cryptographic signature of an asset does not verify. |
| `ASSET_SIZE_MISMATCH` | ERROR | The declared size of an asset does not match its actual size. |
| `ASSET_TOO_LARGE` | ERROR | An individual asset exceeds the maximum allowed size. |
| `CALENDAR_CURRENT_DATE_DAY_OUT_OF_RANGE` | ERROR | The calendar current-date day is outside the valid range for its month. |
| `CALENDAR_CURRENT_DATE_MONTH_OUT_OF_RANGE` | ERROR | The calendar current-date month index is outside the valid range. |
| `CALENDAR_MONTH_ARRAYS_DIFFERENT_LENGTH` | ERROR | The month-lengths and month-names arrays have different lengths. |
| `CALENDAR_MONTH_LENGTHS_EMPTY` | ERROR | The month-lengths array is empty. |
| `CALENDAR_MONTH_LENGTH_NOT_POSITIVE` | ERROR | A month length value is not positive. |
| `CALENDAR_MONTH_NAMES_EMPTY` | ERROR | The month-names array is empty. |
| `CALENDAR_WEEKDAY_NAMES_EMPTY` | ERROR | The weekday-names array is empty. |
| `CATALOG_SNAPSHOT_MISMATCH` | ERROR | The catalog snapshot referenced in the package differs from the current server catalog. |
| `COMBAT_LOG_EXCLUDED_BUT_NON_EMPTY` | WARNING | Combat log data is present even though the import excluded it. |
| `COMBAT_LOG_SEQUENCE_EXCEEDS_ENCOUNTER` | ERROR | A combat log sequence number exceeds the number of rounds in the encounter. |
| `COMBAT_LOG_SEQUENCE_NOT_STRICTLY_INCREASING` | ERROR | Combat log sequence numbers are not strictly increasing. |
| `COMPRESSION_RATIO_EXCEEDED` | ERROR | The compression ratio of an entry exceeds the allowed maximum. |
| `CROSS_QUEST_DEPENDENCY` | ERROR | A quest depends on a quest in a different campaign. |
| `DAMAGE_TYPE_REQUIRED` | ERROR | A damage expression is present without damage types. |
| `DEPENDENCY_CYCLE` | ERROR | A circular dependency was detected between entities. |
| `DICE_HISTORY_EXCLUDED_BUT_NON_EMPTY` | WARNING | Dice history data is present even though the import excluded it. |
| `DTO_SCHEMA_DRIFT` | WARNING | The internal DTO structure does not match the expected schema. |
| `DUPLICATE_DEPENDENCY` | WARNING | A dependency is declared more than once. |
| `DUPLICATE_DISARM_KEY` | ERROR | Two disarm methods share the same key. |
| `DUPLICATE_KEY` | ERROR | A key appears more than once in the same namespace. |
| `DUPLICATE_MANIFEST` | ERROR | More than one manifest entry maps to the same key. |
| `DUPLICATE_NORMALIZED_PATH` | ERROR | Two entries resolve to the same normalized path. |
| `DUPLICATE_PROVIDER_REFERENCE` | ERROR | An audio cue duplicates another cue's normalized provider reference in the campaign. |
| `DUPLICATE_REFERENCE` | WARNING | A reference value appears more than once where uniqueness is required. |
| `DUPLICATE_REGION_KEY` | ERROR | A placement region key appears more than once. |
| `DUPLICATE_SESSION_ATTENDEE` | WARNING | The same attendee appears more than once in a session. |
| `DUPLICATE_SESSION_SCENE_VISIT` | WARNING | The same scene visit appears more than once in a session. |
| `ENCRYPTED_ENTRY` | ERROR | A package entry is encrypted and cannot be processed. |
| `GRID_MISMATCH` | WARNING | The grid configuration in the package does not match the target map. |
| `HAZARD_EXPOSURE_REQUIRED` | ERROR | A hazard is missing an exposure mode. |
| `INVALID_ACTIVE_TURN` | ERROR | The active turn reference does not point at a valid combatant. |
| `INVALID_ASSIGNMENT_SOURCE` | ERROR | The source of an assignment is not valid. |
| `INVALID_COMBATANT_KIND` | ERROR | The combatant kind value is not one of the recognized types. |
| `INVALID_COMPLETION_MODE` | ERROR | The completion mode value is not valid. |
| `INVALID_CURRENT_SCENE_REF` | ERROR | The current-scene reference does not match any known scene. |
| `INVALID_DAMAGE_EXPRESSION` | ERROR | A damage expression is not a valid dice expression. |
| `INVALID_GEOMETRY` | ERROR | A geometric shape has invalid or degenerate coordinates. |
| `INVALID_GIVER` | ERROR | The giver reference in a quest is not valid. |
| `INVALID_HANDOUT_DERIVATIVE_METADATA` | ERROR | Handout derivative provenance is incomplete or attached to a non-derivative handout. |
| `INVALID_JSON` | ERROR | The content is not valid JSON. |
| `INVALID_PARTY_CURRENT_HP` | ERROR | The party member current HP value is invalid. |
| `INVALID_REFERENCE_TYPE` | ERROR | A reference points at an entity of the wrong type. |
| `INVALID_RESOURCE_STATE` | ERROR | A resource is in an unexpected state for the requested operation. |
| `INVALID_SESSION_PRESENTATION` | ERROR | The session presentation mode is not valid. |
| `INVALID_STATE` | ERROR | An entity is in an invalid state for the requested operation. |
| `INVALID_THREAT_REFERENCE_KIND` | ERROR | A scene section, combatant, or pin references the wrong threat kind. |
| `INVALID_THREAT_REFERENCE_ROLE_TYPE` | ERROR | A threat reference role does not match its target type. |
| `INVALID_TOKEN_KIND` | ERROR | The token kind value is not recognized. |
| `INVALID_TRANSITION_TARGET` | ERROR | The target of a scene transition is not valid. |
| `LEDGER_DATE_OUT_OF_RANGE` | ERROR | A ledger entry date falls outside the campaign timeline. |
| `LEGACY_REFERENCE_MIGRATED` | INFO | A legacy-format reference was automatically migrated. |
| `LEGACY_STATE_DEFAULTED` | INFO | A legacy state field was defaulted because its value is no longer valid. |
| `MANIFEST_MISSING` | ERROR | The package does not contain a manifest entry. |
| `MANIFEST_TOO_LARGE` | ERROR | The manifest entry exceeds the maximum allowed size. |
| `MAP_GRID_MISMATCH` | WARNING | The map grid dimensions in the package differ from the target map. |
| `MIGRATION_ERROR` | ERROR | An error occurred during data migration. |
| `MISSING_SESSION_DRAFT` | ERROR | A session references a draft that does not exist. |
| `MISSING_SOURCE_ANNOTATION` | ERROR | A required source annotation is missing. |
| `MULTIPLE_ACTIVE_ENCOUNTERS` | ERROR | More than one encounter is active at the same time. |
| `MULTIPLE_GIVERS` | ERROR | A quest has more than one giver. |
| `NON_CAMPAIGN_CUSTOM_DEPENDENCY` | WARNING | A custom dependency references content outside the campaign. |
| `OUT_OF_BOUNDS` | ERROR | A numeric value is outside its expected range. |
| `PACKAGE_EXPANDED_TOO_LARGE` | ERROR | The expanded package exceeds the maximum allowed size. |
| `PACKAGE_READ_ERROR` | ERROR | The package could not be read. |
| `PACKAGE_TOO_LARGE` | ERROR | The compressed package exceeds the maximum allowed size. |
| `PATH_TOO_LONG` | ERROR | A path within the package exceeds the maximum allowed length. |
| `SCHEMA_VIOLATION` | ERROR | The content does not conform to the expected JSON schema. |
| `SELF_DEPENDENCY` | ERROR | An entity depends on itself. |
| `SESSION_VISITS_NOT_MONOTONIC` | ERROR | Session scene visits are not in monotonic order. |
| `SESSION_VISIT_COMPLETES_BEFORE_VISIT` | ERROR | A session scene visit is completed before it was visited. |
| `SYMLINK_ENTRY` | ERROR | The package contains a symbolic link entry. |
| `THREAT_CHECK_MISSING_ABILITY_OR_SKILL` | ERROR | A CHECK is missing ability/skill or a SAVE is missing ability. |
| `THREAT_CHECK_SAVE_HAS_SKILL` | ERROR | A SAVE threat check includes a skill. |
| `THREAT_DC_OUT_OF_BOUNDS` | ERROR | A DC or passive value is outside the allowed range. |
| `THREAT_LEVEL_INVALID` | ERROR | A threat level band is outside 1–20 or inverted. |
| `THREAT_PIN_OUT_OF_BOUNDS` | ERROR | A map threat pin is outside the map pixel bounds. |
| `THREAT_SEVERITY_REQUIRED` | ERROR | A trap or hazard is missing severity. |
| `TIMELINE_DATE_OUT_OF_RANGE` | ERROR | A timeline event date falls outside the campaign timeline range. |
| `TOKEN_OUT_OF_BOUNDS` | ERROR | A token is positioned outside the map boundaries. |
| `TOO_MANY_ENTRIES` | ERROR | The package contains more entries than the allowed maximum. |
| `TRAP_EFFECT_MODE_CONFLICT` | ERROR | A trap declares both an attack bonus and a saving throw. |
| `TRAP_RESET_TIMING_REQUIRED` | ERROR | An AUTOMATIC trap reset is missing reset timing. |
| `TRAVERSAL_ASSET_PATH` | ERROR | An asset path attempts directory traversal outside the package. |
| `UNEXPECTED_ROOT_ENTRY` | ERROR | An unexpected entry was found at the package root. |
| `UNEXPECTED_SESSION_DRAFT` | WARNING | A session draft entry was found where it was not expected. |
| `UNRESOLVED_ASSET_REFERENCE` | ERROR | A reference to an asset could not be resolved. |
| `UNRESOLVED_CATALOG_REFERENCE` | ERROR | A reference to a catalog key could not be resolved. |
| `UNRESOLVED_PLACEMENT_REGION` | ERROR | A placement region reference could not be resolved. |
| `UNRESOLVED_REFERENCE` | ERROR | A typed reference points at a missing package or catalog key. |
| `UNRESOLVED_WAVE_REFERENCE` | ERROR | A wave reference in an encounter could not be resolved. |
| `UNSUPPORTED_FORMAT_VERSION` | ERROR | The package format version is not supported by this server. |
| `UNSUPPORTED_MEDIA_TYPE` | ERROR | The media type of an entry is not supported. |
| `VALIDATION_ERROR` | ERROR | A generic validation error occurred. |
