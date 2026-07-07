#!/usr/bin/env python3
"""Fetch D&D 5.5e SRD 5.2 monster data from open5e.com (v2 API).
Generates src/main/resources/srd/srd-5.2-monsters.json."""

import json, os, sys, time, urllib.request, urllib.error

API_BASE = "https://api.open5e.com"
DOC_FILTER = "srd-2024"
PAGE_SIZE = 100
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "srd", "srd-5.2-monsters.json",
)


def format_speed(speed_dict):
    if not speed_dict or not isinstance(speed_dict, dict):
        return ""
    parts = []
    unit = speed_dict.get("unit", "ft.")
    walk = speed_dict.get("walk", 0)
    if walk:
        parts.append(f"{walk} {unit}")
    for mode in ["burrow", "climb", "fly", "swim"]:
        val = speed_dict.get(mode, 0)
        if val:
            if mode == "fly" and speed_dict.get("hover", False):
                parts.append(f"fly {val} {unit} (hover)")
            else:
                parts.append(f"{mode} {val} {unit}")
    return ", ".join(parts)


def format_skills(skills_dict):
    if not skills_dict or not isinstance(skills_dict, dict):
        return ""
    parts = []
    for name in sorted(skills_dict.keys()):
        bonus = skills_dict[name]
        formatted_name = name.replace("_", " ").title()
        sign = "+" if bonus >= 0 else ""
        parts.append(f"{formatted_name} {sign}{bonus}")
    return ", ".join(parts)


def format_senses(creature):
    parts = []
    dr = creature.get("darkvision_range")
    if dr:
        parts.append(f"darkvision {dr} ft.")
    br = creature.get("blindsight_range")
    if br:
        parts.append(f"blindsight {br} ft.")
    tr = creature.get("tremorsense_range")
    if tr:
        parts.append(f"tremorsense {tr} ft.")
    tsr = creature.get("truesight_range")
    if tsr:
        parts.append(f"truesight {tsr} ft.")
    pp = creature.get("passive_perception")
    if pp:
        parts.append(f"passive Perception {pp}")
    return ", ".join(parts)


def format_cr(cr_value):
    if cr_value is None:
        return "0"
    if isinstance(cr_value, float) and cr_value == int(cr_value):
        return str(int(cr_value))
    cr_map = {0.125: "1/8", 0.25: "1/4", 0.5: "1/2"}
    if cr_value in cr_map:
        return cr_map[cr_value]
    return str(cr_value)


def extract_actions_by_type(actions, action_type):
    filtered = [a for a in actions if a.get("action_type") == action_type]
    if not filtered:
        return None
    result = [{"name": a.get("name", ""), "description": a.get("desc", "")} for a in filtered]
    return json.dumps(result)


def api_get(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(
                url, headers={"Accept": "application/json", "User-Agent": "DMHelper/1.0"}
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode())
        except Exception as e:
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)


def main():
    entries = []
    page = 1
    while True:
        url = f"{API_BASE}/v2/creatures/?document__key__in={DOC_FILTER}&limit={PAGE_SIZE}&page={page}"
        data = api_get(url)
        results = data.get("results", [])
        if not results:
            break
        for c in results:
            scores = c.get("ability_scores", {})
            saves = c.get("saving_throws", {})
            resists = c.get("resistances_and_immunities", {})
            actions = c.get("actions", [])

            source_key = c.get("key", "").removeprefix("srd-2024_")

            cr_raw = c.get("challenge_rating")
            cr = format_cr(cr_raw)

            hp_val = c.get("hit_points", 0) or 0
            hit_dice = c.get("hit_dice", "")
            hp_str = f"{hp_val} ({hit_dice})" if hit_dice else str(hp_val)

            traits_list = c.get("traits") or []
            traits_json = (
                json.dumps(
                    [
                        {"name": t.get("name", ""), "description": t.get("desc", "")}
                        for t in traits_list
                    ]
                )
                if traits_list
                else None
            )

            entry = {
                "sourceKey": source_key,
                "name": c.get("name", "Unknown"),
                "size": (c.get("size") or {}).get("name", ""),
                "type": (c.get("type") or {}).get("name", ""),
                "alignment": c.get("alignment", "") or "",
                "ac": c.get("armor_class", 10),
                "hp": hp_str,
                "speed": format_speed(c.get("speed", {})),
                "strScore": scores.get("strength", 10),
                "dexScore": scores.get("dexterity", 10),
                "conScore": scores.get("constitution", 10),
                "intScore": scores.get("intelligence", 10),
                "wisScore": scores.get("wisdom", 10),
                "chaScore": scores.get("charisma", 10),
                "strSave": saves.get("strength"),
                "dexSave": saves.get("dexterity"),
                "conSave": saves.get("constitution"),
                "intSave": saves.get("intelligence"),
                "wisSave": saves.get("wisdom"),
                "chaSave": saves.get("charisma"),
                "skills": format_skills(c.get("skill_bonuses", {})),
                "damageVulnerabilities": (resists.get("damage_vulnerabilities_display") or ""),
                "damageResistances": (resists.get("damage_resistances_display") or ""),
                "damageImmunities": (resists.get("damage_immunities_display") or ""),
                "conditionImmunities": (resists.get("condition_immunities_display") or ""),
                "senses": format_senses(c),
                "languages": (c.get("languages") or {}).get("as_string", ""),
                "traits": traits_json,
                "actions": extract_actions_by_type(actions, "ACTION"),
                "bonusActions": extract_actions_by_type(actions, "BONUS_ACTION"),
                "reactions": extract_actions_by_type(actions, "REACTION"),
                "legendaryActions": extract_actions_by_type(actions, "LEGENDARY_ACTION"),
                "legendaryDescription": c.get("legendary_desc", "") or "",
                "lairActions": extract_actions_by_type(actions, "LAIR_ACTION"),
                "cr": cr,
                "xp": c.get("experience_points", 0) or 0,
            }
            entries.append(entry)

        print(f"  Page {page}: {len(results)} creatures ({len(entries)} total)")
        if not data.get("next"):
            break
        page += 1

    entries.sort(key=lambda e: e["name"])
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
    print(f"Wrote {len(entries)} monsters to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
