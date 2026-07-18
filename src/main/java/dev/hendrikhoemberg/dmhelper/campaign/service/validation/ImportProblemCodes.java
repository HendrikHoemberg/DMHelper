package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ImportProblemCodes {
    private ImportProblemCodes() {}

    public static final String AMBIGUOUS_REFERENCE = "AMBIGUOUS_REFERENCE";
    public static final String ASSET_DIGEST_MISMATCH = "ASSET_DIGEST_MISMATCH";
    public static final String ASSET_EXTENSION_MISMATCH = "ASSET_EXTENSION_MISMATCH";
    public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
    public static final String ASSET_PATH_INVALID = "ASSET_PATH_INVALID";
    public static final String ASSET_READ_ERROR = "ASSET_READ_ERROR";
    public static final String ASSET_SIGNATURE_MISMATCH = "ASSET_SIGNATURE_MISMATCH";
    public static final String ASSET_SIZE_MISMATCH = "ASSET_SIZE_MISMATCH";
    public static final String ASSET_TOO_LARGE = "ASSET_TOO_LARGE";
    public static final String CALENDAR_CURRENT_DATE_DAY_OUT_OF_RANGE = "CALENDAR_CURRENT_DATE_DAY_OUT_OF_RANGE";
    public static final String CALENDAR_CURRENT_DATE_MONTH_OUT_OF_RANGE = "CALENDAR_CURRENT_DATE_MONTH_OUT_OF_RANGE";
    public static final String CALENDAR_MONTH_ARRAYS_DIFFERENT_LENGTH = "CALENDAR_MONTH_ARRAYS_DIFFERENT_LENGTH";
    public static final String CALENDAR_MONTH_LENGTHS_EMPTY = "CALENDAR_MONTH_LENGTHS_EMPTY";
    public static final String CALENDAR_MONTH_LENGTH_NOT_POSITIVE = "CALENDAR_MONTH_LENGTH_NOT_POSITIVE";
    public static final String CALENDAR_MONTH_NAMES_EMPTY = "CALENDAR_MONTH_NAMES_EMPTY";
    public static final String CALENDAR_WEEKDAY_NAMES_EMPTY = "CALENDAR_WEEKDAY_NAMES_EMPTY";
    public static final String CATALOG_SNAPSHOT_MISMATCH = "CATALOG_SNAPSHOT_MISMATCH";
    public static final String COMBAT_LOG_EXCLUDED_BUT_NON_EMPTY = "COMBAT_LOG_EXCLUDED_BUT_NON_EMPTY";
    public static final String COMBAT_LOG_SEQUENCE_EXCEEDS_ENCOUNTER = "COMBAT_LOG_SEQUENCE_EXCEEDS_ENCOUNTER";
    public static final String COMBAT_LOG_SEQUENCE_NOT_STRICTLY_INCREASING = "COMBAT_LOG_SEQUENCE_NOT_STRICTLY_INCREASING";
    public static final String COMPRESSION_RATIO_EXCEEDED = "COMPRESSION_RATIO_EXCEEDED";
    public static final String CROSS_QUEST_DEPENDENCY = "CROSS_QUEST_DEPENDENCY";
    public static final String DEPENDENCY_CYCLE = "DEPENDENCY_CYCLE";
    public static final String DICE_HISTORY_EXCLUDED_BUT_NON_EMPTY = "DICE_HISTORY_EXCLUDED_BUT_NON_EMPTY";
    public static final String DTO_SCHEMA_DRIFT = "DTO_SCHEMA_DRIFT";
    public static final String DUPLICATE_DEPENDENCY = "DUPLICATE_DEPENDENCY";
    public static final String DUPLICATE_KEY = "DUPLICATE_KEY";
    public static final String DUPLICATE_MANIFEST = "DUPLICATE_MANIFEST";
    public static final String DUPLICATE_NORMALIZED_PATH = "DUPLICATE_NORMALIZED_PATH";
    public static final String DUPLICATE_REFERENCE = "DUPLICATE_REFERENCE";
    public static final String DUPLICATE_REGION_KEY = "DUPLICATE_REGION_KEY";
    public static final String DUPLICATE_SESSION_ATTENDEE = "DUPLICATE_SESSION_ATTENDEE";
    public static final String DUPLICATE_SESSION_SCENE_VISIT = "DUPLICATE_SESSION_SCENE_VISIT";
    public static final String ENCRYPTED_ENTRY = "ENCRYPTED_ENTRY";
    public static final String GRID_MISMATCH = "GRID_MISMATCH";
    public static final String INVALID_ACTIVE_TURN = "INVALID_ACTIVE_TURN";
    public static final String INVALID_ASSIGNMENT_SOURCE = "INVALID_ASSIGNMENT_SOURCE";
    public static final String INVALID_COMBATANT_KIND = "INVALID_COMBATANT_KIND";
    public static final String INVALID_COMPLETION_MODE = "INVALID_COMPLETION_MODE";
    public static final String INVALID_CURRENT_SCENE_REF = "INVALID_CURRENT_SCENE_REF";
    public static final String INVALID_GEOMETRY = "INVALID_GEOMETRY";
    public static final String INVALID_GIVER = "INVALID_GIVER";
    public static final String INVALID_JSON = "INVALID_JSON";
    public static final String INVALID_PARTY_CURRENT_HP = "INVALID_PARTY_CURRENT_HP";
    public static final String INVALID_REFERENCE_TYPE = "INVALID_REFERENCE_TYPE";
    public static final String INVALID_RESOURCE_STATE = "INVALID_RESOURCE_STATE";
    public static final String INVALID_SESSION_PRESENTATION = "INVALID_SESSION_PRESENTATION";
    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String INVALID_TOKEN_KIND = "INVALID_TOKEN_KIND";
    public static final String INVALID_TRANSITION_TARGET = "INVALID_TRANSITION_TARGET";
    public static final String LEDGER_DATE_OUT_OF_RANGE = "LEDGER_DATE_OUT_OF_RANGE";
    public static final String LEGACY_REFERENCE_MIGRATED = "LEGACY_REFERENCE_MIGRATED";
    public static final String LEGACY_STATE_DEFAULTED = "LEGACY_STATE_DEFAULTED";
    public static final String MANIFEST_MISSING = "MANIFEST_MISSING";
    public static final String MANIFEST_TOO_LARGE = "MANIFEST_TOO_LARGE";
    public static final String MAP_GRID_MISMATCH = "MAP_GRID_MISMATCH";
    public static final String MIGRATION_ERROR = "MIGRATION_ERROR";
    public static final String MISSING_SESSION_DRAFT = "MISSING_SESSION_DRAFT";
    public static final String MISSING_SOURCE_ANNOTATION = "MISSING_SOURCE_ANNOTATION";
    public static final String MULTIPLE_ACTIVE_ENCOUNTERS = "MULTIPLE_ACTIVE_ENCOUNTERS";
    public static final String MULTIPLE_GIVERS = "MULTIPLE_GIVERS";
    public static final String NON_CAMPAIGN_CUSTOM_DEPENDENCY = "NON_CAMPAIGN_CUSTOM_DEPENDENCY";
    public static final String OUT_OF_BOUNDS = "OUT_OF_BOUNDS";
    public static final String PACKAGE_EXPANDED_TOO_LARGE = "PACKAGE_EXPANDED_TOO_LARGE";
    public static final String PACKAGE_TOO_LARGE = "PACKAGE_TOO_LARGE";
    public static final String PACKAGE_READ_ERROR = "PACKAGE_READ_ERROR";
    public static final String PATH_TOO_LONG = "PATH_TOO_LONG";
    public static final String SCHEMA_VIOLATION = "SCHEMA_VIOLATION";
    public static final String SELF_DEPENDENCY = "SELF_DEPENDENCY";
    public static final String SESSION_VISITS_NOT_MONOTONIC = "SESSION_VISITS_NOT_MONOTONIC";
    public static final String SESSION_VISIT_COMPLETES_BEFORE_VISIT = "SESSION_VISIT_COMPLETES_BEFORE_VISIT";
    public static final String SYMLINK_ENTRY = "SYMLINK_ENTRY";
    public static final String TIMELINE_DATE_OUT_OF_RANGE = "TIMELINE_DATE_OUT_OF_RANGE";
    public static final String TOKEN_OUT_OF_BOUNDS = "TOKEN_OUT_OF_BOUNDS";
    public static final String TOO_MANY_ENTRIES = "TOO_MANY_ENTRIES";
    public static final String TRAVERSAL_ASSET_PATH = "TRAVERSAL_ASSET_PATH";
    public static final String UNEXPECTED_ROOT_ENTRY = "UNEXPECTED_ROOT_ENTRY";
    public static final String UNEXPECTED_SESSION_DRAFT = "UNEXPECTED_SESSION_DRAFT";
    public static final String UNRESOLVED_ASSET_REFERENCE = "UNRESOLVED_ASSET_REFERENCE";
    public static final String UNRESOLVED_CATALOG_REFERENCE = "UNRESOLVED_CATALOG_REFERENCE";
    public static final String UNRESOLVED_PLACEMENT_REGION = "UNRESOLVED_PLACEMENT_REGION";
    public static final String UNRESOLVED_REFERENCE = "UNRESOLVED_REFERENCE";
    public static final String UNRESOLVED_WAVE_REFERENCE = "UNRESOLVED_WAVE_REFERENCE";
    public static final String UNSUPPORTED_FORMAT_VERSION = "UNSUPPORTED_FORMAT_VERSION";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String WORLD_LOCATION_CYCLE = "WORLD_LOCATION_CYCLE";
    public static final String WORLD_RELATIONSHIP_SELF = "WORLD_RELATIONSHIP_SELF";
    public static final String FACTION_CLOCK_RANGE = "FACTION_CLOCK_RANGE";
    public static final String INVALID_WORLD_REFERENCE_TYPE = "INVALID_WORLD_REFERENCE_TYPE";
    public static final String INVALID_TABLE_EXPRESSION = "INVALID_TABLE_EXPRESSION";
    public static final String INVALID_QUANTITY_EXPRESSION = "INVALID_QUANTITY_EXPRESSION";
    public static final String TABLE_RANGE_GAP = "TABLE_RANGE_GAP";
    public static final String TABLE_RANGE_OVERLAP = "TABLE_RANGE_OVERLAP";
    public static final String TABLE_RANGE_BOUNDS = "TABLE_RANGE_BOUNDS";
    public static final String TABLE_WEIGHT_INVALID = "TABLE_WEIGHT_INVALID";
    public static final String INVALID_TABLE_REFERENCE_TYPE = "INVALID_TABLE_REFERENCE_TYPE";
    public static final String TABLE_REFERENCE_CYCLE = "TABLE_REFERENCE_CYCLE";
    public static final String TABLE_REFERENCE_DEPTH_EXCEEDED = "TABLE_REFERENCE_DEPTH_EXCEEDED";

    private static final Set<String> ALL;

    static {
        Set<String> codes = new LinkedHashSet<>();
        for (var field : ImportProblemCodes.class.getFields()) {
            if (field.getType() == String.class) {
                try {
                    codes.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }
        }
        ALL = Collections.unmodifiableSet(codes);
    }

    public static Set<String> all() {
        return ALL;
    }
}
