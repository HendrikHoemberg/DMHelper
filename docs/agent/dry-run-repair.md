# Dry-run Repair Guide

## Schema error example

**Command to preview:**
```bash
curl -X POST http://localhost:8080/campaigns/package-imports/previews \
  -H "Content-Type: application/json" \
  -d @src/test/resources/docs-examples/schema-error.dmcampaign.json
```

**Sample problem JSON:**
```json
{
  "severity": "ERROR",
  "code": "SCHEMA_VIOLATION",
  "path": "/extraUnknownField",
  "message": "Additional property 'extraUnknownField' is not permitted",
  "suggestion": "Remove the unrecognized property."
}
```

**Fix:** Remove the `extraUnknownField` property from the root object.

```diff
-  "extraUnknownField": true
```

**Re-validate success:** The `SCHEMA_VIOLATION` error disappears. The document now
conforms to `campaign-format-v2.schema.json` which sets `"additionalProperties": false`.

---

## Semantic error example

**Command to preview:**
```bash
curl -X POST http://localhost:8080/campaigns/package-imports/previews \
  -H "Content-Type: application/json" \
  -d @src/test/resources/docs-examples/semantic-error.dmcampaign.json
```

**Sample problem JSON:**
```json
{
  "severity": "ERROR",
  "code": "UNRESOLVED_CATALOG_REFERENCE",
  "path": "/party/sheet/speciesRef/sourceKey",
  "message": "Catalog reference 'definitely-not-a-real-species' could not be resolved in catalog",
  "suggestion": "Use a catalog key that exists in the target catalog."
}
```

**Fix:** Replace the `speciesRef.sourceKey` with a valid catalog entry.

```diff
  "speciesRef": {
    "scope": "CATALOG",
    "type": "SPECIES",
    "ruleset": "SRD_5_2",
-   "sourceKey": "definitely-not-a-real-species"
+   "sourceKey": "human"
  }
```

**Re-validate success:** The `UNRESOLVED_REFERENCE` / `UNRESOLVED_CATALOG_REFERENCE`
error is gone.

---

## General UNRESOLVED_REFERENCE repair

When the validator reports `UNRESOLVED_REFERENCE`, it means a typed `contentReference`
(either PACKAGE or CATALOG scope) does not find its target.

**Diagnosis steps:**
1. Check the `path` in the problem to identify which reference is broken.
2. For `CATALOG` scope: verify the `sourceKey` exists via `GET /api/v1/catalog`.
3. For `PACKAGE` scope: verify the `key` exists in the corresponding content array.
4. Never guess a valid key — use the catalog API or documented SRD keys.

**Common causes and fixes:**

| Cause | Fix |
|-------|-----|
| Typo in `sourceKey` | Correct the key to match the catalog entry |
| Missing custom entity | Add the entity to the appropriate array (e.g., `customStatBlocks`) |
| Wrong `scope` | Change scope from `PACKAGE` to `CATALOG` (or vice versa) |
| Deleted content still referenced | Remove or update the stale reference |
| Case mismatch | Keys are case-sensitive; match the exact value |

**Example fix for a missing package entity:**
```diff
  "statblockRef": {
    "scope": "PACKAGE",
    "type": "STATBLOCK",
-   "key": "my-custom-monster"
+   "key": "my-custom-monster-v2"
  }
```
