# Import Preview

## Overview

When you import a campaign package (`.dmcampaign` ZIP or `.dmcampaign.json`), the server first creates a **preview** — no data is modified until you confirm.

## Preview Counts

The preview response includes counts of each content type that would be imported or updated: campaigns, adventures, scenes, encounters, maps, tokens, party members, sheets, notes, handouts, and more.

## Warnings vs Errors

| Severity | Meaning | Import allowed? |
|----------|---------|----------------|
| `ERROR` | Data integrity problem (broken reference, duplicate key, missing asset) | No — preview status is `BLOCKED` |
| `WARNING` | Advisory issue (ambiguous reference, excluded combat log, duplicate dependency) | Yes — if you check `acceptWarnings` |

Errors and warnings map to codes in the [Validation Error Catalog](../authoring/validation-errors.md).

## Confirm Flow

1. Upload a package file → server returns a preview with `previewId`, status, errors, warnings
2. If no `ERROR` severity issues are present, the **Confirm** button is enabled
3. Optionally check `acceptWarnings` to proceed despite warnings
4. `POST /campaigns/package-imports/{previewId}/confirm?acceptWarnings=<bool>` commits the import
5. On success, you are redirected to the imported campaign

## Additive Import

Import is additive — existing data is merged, not replaced. Package version 2 supports migrations from version 1. Use the endpoint `GET /campaigns/package-imports/previews` to initiate.
