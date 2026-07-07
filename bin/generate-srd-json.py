#!/usr/bin/env python3
"""Fetch D&D 5.5e SRD 5.2 monsters from open5e.com and generate srd-5.2-monsters.json.

Uses the open5e v2 API (https://api.open5e.com/v2/) filtered to the srd-2024
document, which contains the full CC-BY-4.0 SRD 5.2 (2024/5.5e rules) bestiary.

Transforms each open5e creature into the flat JSON schema that DMHelper's SRD
seed loader expects.  331 monsters as of open5e's current srd-2024 document.
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

API_BASE = "https://api.open5e.com"
DOCUMENT_KEY = "srd-2024"
PAGE_SIZE = 50  # paginate — large pages time out on open5e

def _index_url(page: int = 1) -> str:
    return (
        f"{API_BASE}/v2/creatures/"
        f"?document__key__in={DOCUMENT_KEY}"
        f"&limit={PAGE_SIZE}&page={page}"
    )

OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "srd", "srd-5.2-monsters.json",
)

SAVE_NAMES = {
    "strength": "strSave", "dexterity": "dexSave", "constitution": "conSave",
    "intelligence": "intSave", "wisdom": "wisSave", "charisma": "chaSave",
}

ABILITY_KEYS = [
    ("strength", "strScore"), ("dexterity", "dexScore"),
    ("constitution", "conScore"), ("intelligence", "intScore"),
    ("wisdom", "wisScore"), ("charisma", "chaScore"),
]

CR_FRACTION_MAP = {0.125: "1/8", 0.25: "1/4", 0.5: "1/2"}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def api_get(url: str, retries: int = 3) -> dict:
    """GET *url* and return parsed JSON, with simple retry logic."""
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={
                "Accept": "application/json",
                "User-Agent": "DMHelper/1.0",
            })
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode())
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            if attempt == retries - 1:
                raise
            wait = 2 ** attempt
            print(f"  ⚠ {exc} — retrying in {wait}s …")
            time.sleep(wait)


def cr_to_string(cr: float) -> str:
    """Convert numeric CR to display string (e.g. 0.25 → '1/4')."""
    if cr in CR_FRACTION_MAP:
        return CR_FRACTION_MAP[cr]
    return str(int(cr))


def format_speed(speed: dict) -> str:
    """open5e speed dict → '30 ft., fly 80 ft., swim 40 ft.'"""
    parts = []
    walk = speed.get("walk")
    if walk and walk > 0:
        parts.append(f"{walk} ft.")
    for mode in ("burrow", "climb", "fly", "swim"):
        val = speed.get(mode)
        if val and val > 0:
            parts.append(f"{mode} {val} ft.")
    return ", ".join(parts) if parts else "0 ft."


def format_senses(m: dict) -> str:
    """Extract sense ranges + passive perception into a string."""
    parts = []
    for key, label in [
        ("blindsight_range", "blindsight"),
        ("darkvision_range", "darkvision"),
        ("tremorsense_range", "tremorsense"),
        ("truesight_range", "truesight"),
    ]:
        val = m.get(key)
        if val and val > 0:
            parts.append(f"{label} {val} ft.")
    pp = m.get("passive_perception")
    if pp is not None:
        parts.append(f"passive Perception {pp}")
    return ", ".join(parts)


def format_skills(skill_bonuses: dict) -> str | None:
    """open5e skill_bonuses dict → 'Perception +11, Stealth +7'."""
    if not skill_bonuses:
        return None
    items = []
    for name, bonus in skill_bonuses.items():
        display = name.replace("_", " ").title()
        sign = "+" if bonus >= 0 else ""
        items.append(f"{display} {sign}{bonus}")
    return ", ".join(items)


def extract_saves(m: dict) -> dict:
    """Return a dict of {strSave: N, ...} for proficiency saves only.

    open5e returns all six saves in ``saving_throws``, but many are just the
    ability modifier.  We only store saves where the creature is proficient
    (saving_throws value differs from the raw modifier).
    """
    modifiers = m.get("modifiers", {})
    saves = m.get("saving_throws", {})
    result = {}
    for stat_key, save_key in SAVE_NAMES.items():
        save_val = saves.get(stat_key)
        mod_val = modifiers.get(stat_key)
        if save_val is not None and mod_val is not None and save_val != mod_val:
            result[save_key] = save_val
    return result


def format_damage_display(display_str: str) -> str | None:
    """'acid, fire' → 'acid, fire' (lowercased).  None / '' → None."""
    if not display_str:
        return None
    return display_str.lower()


def transform_action_entries(entries: list) -> str:
    """Turn open5e action/trait objects into a JSON string of [{name, description}]."""
    result = []
    for entry in entries:
        name = entry.get("name", "")
        desc = entry.get("desc", "")
        # Attach usage limits to name (e.g. "Acid Breath (Recharge 5–6)")
        usage = entry.get("usage_limits")
        if usage:
            utype = usage.get("type", "")
            if utype == "RECHARGE_ON_ROLL":
                param = usage.get("param", 6)
                if param <= 6:
                    name = f"{name} (Recharge {param}–6)"
                else:
                    name = f"{name} (Recharge 6)"
            elif utype in ("PER_DAY", "USES_PER_DAY"):
                times = usage.get("param", 1)
                name = f"{name} ({times}/Day)"
            elif utype == "RECHARGE_AFTER_REST":
                times = usage.get("param", 1)
                name = f"{name} ({times}/Rest)"
        result.append({"name": name, "description": desc})
    return json.dumps(result)


def build_legendary_description(m: dict) -> str | None:
    """Generate standard legendary action description text."""
    legendary = [
        a for a in m.get("actions", [])
        if a.get("action_type") == "LEGENDARY_ACTION"
    ]
    if not legendary:
        return None
    # Determine number of legendary actions from max cost
    max_cost = max(
        (a.get("legendary_action_cost") or 0 for a in legendary),
        default=3,
    )
    name = m["name"].lower()
    return (
        f"The {name} can take {max_cost} legendary actions, choosing from "
        f"the options below. Only one legendary action option can be used "
        f"at a time and only at the end of another creature's turn. "
        f"The {name} regains spent legendary actions at the start of its turn."
    )


# ---------------------------------------------------------------------------
# Main transform
# ---------------------------------------------------------------------------

def transform_creature(m: dict) -> dict:
    """Transform one open5e creature into DMHelper's flat JSON schema."""
    abilities = m.get("ability_scores", {})
    ri = m.get("resistances_and_immunities", {})
    langs = m.get("languages", {})

    # Build HP string
    hp = m.get("hit_points", 0)
    hd = m.get("hit_dice", "")
    hp_str = f"{hp} ({hd})" if hd else str(hp)

    # Split actions by type
    all_actions = m.get("actions", []) or []
    actions_list = [a for a in all_actions if a.get("action_type") == "ACTION"]
    bonus_actions = [a for a in all_actions if a.get("action_type") == "BONUS_ACTION"]
    reactions = [a for a in all_actions if a.get("action_type") == "REACTION"]
    legendary = [a for a in all_actions if a.get("action_type") == "LEGENDARY_ACTION"]

    # Alignment — open5e lowercase; title-case it
    alignment = m.get("alignment", "")
    if alignment:
        alignment = alignment.title()

    result = {
        "sourceKey": m["key"].removeprefix(f"{DOCUMENT_KEY}_"),
        "name": m["name"],
        "size": (m.get("size") or {}).get("name", "Medium"),
        "type": (m.get("type") or {}).get("name", "Humanoid"),
        "alignment": alignment,
        "ac": m.get("armor_class", 10),
        "hp": hp_str,
        "speed": format_speed(m.get("speed", {})),
    }

    # Ability scores
    for api_key, dm_key in ABILITY_KEYS:
        result[dm_key] = abilities.get(api_key, 10)

    # Proficiency saves
    result.update(extract_saves(m))

    # Skills
    skills_str = format_skills(m.get("skill_bonuses", {}))
    if skills_str:
        result["skills"] = skills_str

    # Damage / condition
    for api_field, dm_field in [
        ("damage_vulnerabilities_display", "damageVulnerabilities"),
        ("damage_resistances_display", "damageResistances"),
        ("damage_immunities_display", "damageImmunities"),
        ("condition_immunities_display", "conditionImmunities"),
    ]:
        val = format_damage_display(ri.get(api_field, ""))
        if val:
            result[dm_field] = val

    # Senses
    result["senses"] = format_senses(m)

    # Languages
    lang_str = langs.get("as_string", "")
    if lang_str:
        result["languages"] = lang_str

    # Traits
    traits = m.get("traits", []) or []
    if traits:
        result["traits"] = transform_action_entries(traits)

    # Actions
    if actions_list:
        result["actions"] = transform_action_entries(actions_list)
    if bonus_actions:
        result["bonusActions"] = transform_action_entries(bonus_actions)
    if reactions:
        result["reactions"] = transform_action_entries(reactions)

    # Legendary
    if legendary:
        result["legendaryActions"] = transform_action_entries(legendary)
        legend_desc = build_legendary_description(m)
        if legend_desc:
            result["legendaryDescription"] = legend_desc

    # CR
    result["cr"] = cr_to_string(m.get("challenge_rating", 0))

    return result


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

def main():
    print(f"Fetching SRD 5.2 monsters from open5e /v2/creatures/ (document={DOCUMENT_KEY}) …")
    page = 1
    page_data = api_get(_index_url(page))
    total = page_data.get("count", 0)
    entries = page_data.get("results", [])
    print(f"  Total: {total} monsters.  Page 1: {len(entries)} received.")

    while page_data.get("next"):
        page += 1
        print(f"  Fetching page {page} …", end="", flush=True)
        page_data = api_get(page_data["next"])
        more = page_data.get("results", [])
        entries.extend(more)
        print(f" {len(more)} received ({len(entries)} total)")

    if not entries:
        print("ERROR: No monsters retrieved. Check API availability.")
        sys.exit(1)

    print(f"  Transforming {len(entries)} monsters …")

    monsters = []
    errors = []
    for entry in entries:
        name = entry.get("name", "?")
        try:
            monsters.append(transform_creature(entry))
        except Exception as exc:
            print(f"  ✗ {name}: {exc}")
            errors.append((name, str(exc)))

    monsters.sort(key=lambda m: m["name"])

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w") as f:
        json.dump(monsters, f, indent=2, ensure_ascii=False)

    print(f"\nWrote {len(monsters)} monsters to {OUTPUT_PATH}")
    print("Source: open5e.com srd-2024 (D&D 5.5e / SRD 5.2, CC-BY-4.0)")

    if errors:
        print(f"\n⚠ {len(errors)} monster(s) failed:")
        for slug, err in errors:
            print(f"  - {slug}: {err}")
        sys.exit(1)


if __name__ == "__main__":
    main()
