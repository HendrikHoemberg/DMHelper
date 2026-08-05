-- Cockpit layouts became fixed presets: four built-in layouts selected client-side and
-- remembered in localStorage. User-defined presets, their versioning and their optimistic
-- locking are gone, so the table has no remaining reader or writer.
-- See docs/superpowers/specs/2026-08-04-overengineering-remediation-design.md section 6.
DROP TABLE IF EXISTS cockpit_layout_preset;
