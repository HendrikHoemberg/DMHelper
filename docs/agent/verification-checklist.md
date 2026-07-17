# Verification Checklist

- [ ] `formatVersion` is `2`
- [ ] capability manifest `agent.sdk` is SUPPORTED for this app revision
- [ ] catalog `sha256` recorded in converter output
- [ ] schema validate (campaign + each map document)
- [ ] dry-run: zero ERROR
- [ ] warnings reviewed and accepted consciously
- [ ] flagship-shaped smoke: cockpit, encounter, handout, export/import

## Detailed steps

### Format version
```json
{ "formatVersion": 2 }
```

### Capability check
Confirm the capability manifest at `src/main/resources/agent/capability-manifest.json`
reports `"id": "agent.sdk"` with `"status": "SUPPORTED"`.

### Catalog snapshot
After resolving catalog content, record the sha256 in metadata:
```json
{ "metadata": { "catalogSha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" } }
```

### Schema validation
```bash
./mvnw -pl . -q -Dtest=DocumentationExampleValidationTest#minimalValidHasNoErrors test
```

### Dry-run
```bash
# Preview via app API
curl -X POST http://localhost:8080/campaigns/package-imports/previews \
  -H "Content-Type: application/json" \
  -d @./manifest.json
```

Expect zero ERROR-level problems in the response.

### Flagship smoke
1. Open session cockpit — navigate scenes in the adventure tree
2. Activate an encounter — verify combatants, waves, and initiative order
3. Present a handout — verify asset rendering
4. Export and re-import the campaign as a new package
