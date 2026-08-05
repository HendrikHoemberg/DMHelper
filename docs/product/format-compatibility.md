# Format Compatibility

| Format | Read | Write | Notes |
|---|---|---|---|
| v2 JSON | yes | yes (asset-free) | |
| v2 ZIP `.dmcampaign` | yes | yes when assets present | |

Format v1 was removed on 2026-08-04. A `.dmcampaign.json` file declaring
`formatVersion: 1` is rejected with `UNSUPPORTED_FORMAT_VERSION`.
