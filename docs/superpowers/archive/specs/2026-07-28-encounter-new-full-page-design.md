# Encounter New-Page Rendering Design

## Problem

`GET /campaigns/{campaignId}/encounters/new` always renders the
`encounter/_form :: form` fragment. That response is correct when the request
comes from the encounters page through HTMX, but a normal browser navigation
receives the same fragment without the application shell, navigation, or
stylesheets.

The empty-state action is a normal link, so creating the first encounter
reliably exposes the broken full-page response. Direct navigation and opening
the link in a new tab fail in the same way.

## Design

Use the request-mode split already established by `GameMapController`:

- A request with `HX-Request: true` returns `encounter/_form :: form` so the
  existing inline create interaction remains unchanged.
- A normal request returns a new `encounter/new` full-page template containing
  the standard head, navbar, application navigation, page header, and existing
  encounter form fragment.

The controller continues to prepare the same campaign ID, maps, and audio-cue
model data for both response modes. No service, persistence, or form-submission
behavior changes.

## Error Handling

Existing campaign lookup and global error handling remain responsible for
missing campaigns and rendering failures. This change introduces no new error
states.

## Testing

Controller tests will independently verify:

- Normal navigation selects the full-page `encounter/new` view.
- HTMX navigation selects the existing `encounter/_form :: form` view.

The full-page render smoke test will include the encounter route so future
fragment-only regressions are caught at the rendered-page boundary.
