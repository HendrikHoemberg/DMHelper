# Offline & Troubleshooting

## No Internet Required

DMHelper runs entirely on the local machine. No internet connection, CDN, or remote service is required for normal operation. All assets, rules content, and JavaScript dependencies are bundled in the JAR.

## Backup Restore

To restore from a startup backup:

1. Stop DMHelper
2. Locate `~/.dmhelper/backups/` and find the desired timestamped ZIP
3. Replace `~/.dmhelper/data/` with the contents of the backup ZIP
4. Restart DMHelper

For manual backups, copy the entire `~/.dmhelper/` directory.

## Common Import Errors

When a package import preview returns a `BLOCKED` status or unexpected warnings, consult the [Validation Error Catalog](../authoring/validation-errors.md) for each error code. Common patterns:

| Error code | Typical cause |
|------------|---------------|
| `UNRESOLVED_REFERENCE` | A scene, map, or statblock reference points to a key not present in the package |
| `DUPLICATE_KEY` | Two entities in the package share the same stable key |
| `ASSET_NOT_FOUND` | A referenced image or file is missing from the package archive |
| `CATALOG_SNAPSHOT_MISMATCH` | Package was exported against a different rules catalog version |

If an import succeeds with warnings, review each warning code in the catalog to decide whether the issue affects your campaign.
