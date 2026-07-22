# Handouts

Handouts are images shared with the player table. DMHelper supports four safety classifications that control what reaches players.

## Classifications

| Classification | Presentable | Default for | Description |
|---------------|-------------|-------------|-------------|
| `PLAYER_SAFE` | Yes | N/A | Intended for player eyes. Explicitly classified by the DM. |
| `PLAYER_DERIVATIVE` | Yes | Derivatives | Auto-set when creating a derivative (crop/redaction). |
| `DM_SOURCE` | No | DM-only flag | DM reference material. Requires override to share. |
| `UNREVIEWED` | No | New imports | New or old-package handouts pending DM review. |

`isPresentable()` returns `true` for `PLAYER_SAFE` and `PLAYER_DERIVATIVE` only.

### Classifying a handout

Use the classification controls on the handout card. The underlying form endpoint is:

```
PUT /campaigns/{campaignId}/handouts/{handoutId}/classification
Form parameter: classification=PLAYER_SAFE
```

Changing a classification:
- Detaches the handout from the live presentation if it was presented.
- Clears any derivative provenance (`sourceHandout`, `derivativeRecipe`) unless the classification is already `PLAYER_DERIVATIVE`.

`PLAYER_DERIVATIVE` cannot be set manually — it is an implementation detail of the derivative workflow.

## Derivatives (Crop / Redaction)

A derivative is a `PLAYER_DERIVATIVE` handout created from a source handout by cropping and/or redacting parts of the image.

### Workflow

1. Open a source handout in the canvas editor.
2. Define a **crop** rectangle (dimensions must be positive).
3. Apply zero or more **redaction** rectangles inside the crop area.
4. Edit the cropped result in the canvas (paint over redacted regions).
5. Upload the edited result as a derivative.

### Recipe

The derivative recipe encodes crop/redaction provenance as JSON:

```json
{
  "sourceWidth": 800,
  "sourceHeight": 600,
  "cropX": 0,
  "cropY": 0,
  "cropWidth": 400,
  "cropHeight": 300,
  "redactions": [
    {"x": 50, "y": 50, "width": 30, "height": 20}
  ]
}
```

The controller validates:
- Crop dimensions are positive.
- Crop bounds do not exceed source image dimensions.
- Redaction bounds fall within the crop area.
- Uploaded PNG dimensions match the crop dimensions.

### Source Retention

The derivative stores a `sourceHandout` reference and the full recipe. The source handout is never modified — no metadata change, no file change. When exported, the manifest includes the source reference and recipe for round-trip fidelity.

## Preview

Before presenting, the DM can preview how a handout will look to players. The cockpit first requests the projected state:

```
GET /api/v1/campaigns/{campaignId}/table/handouts/{handoutId}/preview
```

That response includes the classification, `requiresOverride`, and a state whose file URL points to:

```
GET /api/v1/campaigns/{campaignId}/table/handouts/{handoutId}/preview-file
```

The preview file returns the same stored bytes that the player receives from `/player/files/{handoutId}` once the handout is presented. Response headers include `Cache-Control: no-store`.

## Ordinary Presentation

Present a handout to the player table:

```
PUT /api/v1/campaigns/{campaignId}/table/presentation
{
  "mode": "HANDOUT",
  "ref": "<handoutId>"
}
```

Only handouts where `isPresentable()` is true can be presented ordinarily. Presenting a `DM_SOURCE` or `UNREVIEWED` handout returns a non-2xx response.

## Emergency Override

The DM can bypass the safety check with an audited override:

```
PUT /api/v1/campaigns/{campaignId}/table/presentation
{
  "mode": "HANDOUT",
  "ref": "<handoutId>",
  "emergencyOverride": true,
  "acknowledgement": "I understand this may expose DM content"
}
```

The cockpit deliberately uses two explicit UI steps: **Present anyway…**, followed by **Confirm emergency presentation**. The server independently enforces two request conditions:
1. `emergencyOverride` must be `true`.
2. The `acknowledgement` string must match exactly: `"I understand this may expose DM content"`.

If both pass, a `SessionAuditEntry` of type `PRESENTATION_OVERRIDE` is created with the handout title and classification. The override appears in the session draft under a dedicated section.
