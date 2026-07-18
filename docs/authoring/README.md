# Authoring Reference

- [Campaign Format v2](../campaign-format-v2.md) — Full format reference
- [Validation Error Catalog](validation-errors.md) — Error catalog index

See the [flagship fixtures](../campaign-format-v2.md#fixture-locations) and
executable examples for round-trip validation.

The world-graph fixture at `campaigns/v2/world-graph.dmcampaign/` demonstrates world NPCs,
locations, factions, relationships, and faction clocks. World entities use `PACKAGE`-scoped
content references and follow the same typed-reference rules as other campaign sections.

The feature-complete and published-adventure fixtures at `campaigns/v2/feature-complete.dmcampaign/`
and `campaigns/v2/published-adventure-shaped.dmcampaign/` demonstrate rollable tables:
range/weighted entries, nested tables, statblock/item references, scene and location linking,
and d100 encounter tables.
