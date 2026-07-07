#!/usr/bin/env python3
"""Fetch D&D 5.5e SRD 5.2 monster data from open5e.com.
Generates src/main/resources/srd/srd-5.2-monsters.json."""

import json, os, sys, time, urllib.request, urllib.error

API_BASE = "https://api.open5e.com"
DOC_FILTER = "srd-2024"
PAGE_SIZE = 100
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "srd", "srd-5.2-monsters.json",
)

def ability_scores(s):
    """Parse ability_score JSON array into individual int scores."""
    if not s or not isinstance(s, list):
        return [10, 10, 10, 10, 10, 10]
    result = []
    for entry in s:
        if isinstance(entry, dict):
            result.append(entry.get("value", 10))
        else:
            result.append(10)
    while len(result) < 6:
        result.append(10)
    return result[:6]

def ability_saves(s):
    """Parse saves dict into individual save bonuses, null if not proficient."""
    saves = {"str": None, "dex": None, "con": None, "int": None, "wis": None, "cha": None}
    if not s or not isinstance(s, dict):
        return saves
    for abbr, bonus in s.items():
        key = abbr.lower()[:3]
        if key in saves:
            saves[key] = bonus if isinstance(bonus, int) else int(bonus)
    return saves

def calc_xp(cr_value, cr_text=""):
    """Calculate XP from CR using 2024 DMG table.
    For fractional CR: 0 = 10, 1/8 = 25, 1/4 = 50, 1/2 = 100.
    For integer CR 1-30: mapped to standard XP values."""
    cr_map = {
        0: 10, "0": 10, "0.0": 10,
        "1/8": 25, "0.125": 25,
        "1/4": 50, "0.25": 50,
        "1/2": 100, "0.5": 100,
        1: 200, "1": 200, "1.0": 200,
        2: 450, "2": 450, "2.0": 450,
        3: 700, "3": 700, "3.0": 700,
        4: 1100, "4": 1100, "4.0": 1100,
        5: 1800, "5": 1800, "5.0": 1800,
        6: 2300, "6": 2300, "6.0": 2300,
        7: 2900, "7": 2900, "7.0": 2900,
        8: 3900, "8": 3900, "8.0": 3900,
        9: 5000, "9": 5000, "9.0": 5000,
        10: 5900, "10": 5900, "10.0": 5900,
        11: 7200, "11": 7200, "11.0": 7200,
        12: 8400, "12": 8400, "12.0": 8400,
        13: 10000, "13": 10000, "13.0": 10000,
        14: 11500, "14": 11500, "14.0": 11500,
        15: 13000, "15": 13000, "15.0": 13000,
        16: 15000, "16": 15000, "16.0": 15000,
        17: 18000, "17": 18000, "17.0": 18000,
        18: 20000, "18": 20000, "18.0": 20000,
        19: 22000, "19": 22000, "19.0": 22000,
        20: 25000, "20": 25000, "20.0": 25000,
        21: 33000, "21": 33000, "21.0": 33000,
        22: 41000, "22": 41000, "22.0": 41000,
        23: 50000, "23": 50000, "23.0": 50000,
        24: 62000, "24": 62000, "24.0": 62000,
        25: 75000, "25": 75000, "25.0": 75000,
        26: 90000, "26": 90000, "26.0": 90000,
        27: 105000, "27": 105000, "27.0": 105000,
        28: 120000, "28": 120000, "28.0": 120000,
        29: 135000, "29": 135000, "29.0": 135000,
        30: 155000, "30": 155000, "30.0": 155000,
    }
    if isinstance(cr_value, (int, float)):
        key = cr_value
    else:
        key = str(cr_value).strip()
    return cr_map.get(key, 0)

def api_get(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url,
                headers={"Accept": "application/json", "User-Agent": "DMHelper/1.0"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode())
        except Exception as e:
            if attempt == 2: raise
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
            cr = c.get("challenge_rating", "0")
            if isinstance(cr, str):
                cr = cr.strip()
            elif isinstance(cr, (int, float)):
                cr = str(int(cr)) if cr == int(cr) else str(cr)
            else:
                cr = str(cr)

            scores = ability_scores(c.get("ability_scores"))
            score_list = [abs(scores[0]), abs(scores[1]), abs(scores[2]),
                          abs(int(scores[3]) if len(scores) > 3 else 10),
                          abs(int(scores[4]) if len(scores) > 4 else 10),
                          abs(int(scores[5]) if len(scores) > 5 else 10)]

            saves = ability_saves(c.get("saves"))

            def extract_generic_actions(creature, key_name):
                items = creature.get(key_name, [])
                if not items:
                    return None
                result = []
                for item in items:
                    if isinstance(item, dict):
                        result.append({"name": item.get("name", ""), "description": item.get("desc", "")})
                return json.dumps(result) if result else None

            traits_arr = extract_generic_actions(c, "special_abilities")
            actions_arr = extract_generic_actions(c, "actions")
            legendary_arr = extract_generic_actions(c, "legendary_actions")
            reactions_arr = extract_generic_actions(c, "reactions")

            xp = calc_xp(cr)
            cr_text = c.get("challenge_rating_text", "") or ""

            source_key = c.get("key", "").removeprefix("srd-2024_").removeprefix("srd-2024-")

            entries.append({
                "sourceKey": source_key,
                "name": c.get("name", "Unknown"),
                "size": (c.get("size") or {}).get("name", "") if isinstance(c.get("size"), dict) else (c.get("size") or ""),
                "type": (c.get("type") or {}).get("name", "") if isinstance(c.get("type"), dict) else (c.get("type") or ""),
                "alignment": (c.get("alignment") or {}).get("name", "") if isinstance(c.get("alignment"), dict) else (c.get("alignment") or ""),
                "ac": c.get("armor_class", 10),
                "hp": (c.get("hit_points", 0) or 0),
                "speed": json.dumps(c.get("speed", {})) if isinstance(c.get("speed"), dict) else str(c.get("speed", "")),
                "strScore": score_list[0],
                "dexScore": score_list[1],
                "conScore": score_list[2],
                "intScore": score_list[3],
                "wisScore": score_list[4],
                "chaScore": score_list[5],
                "strSave": saves.get("str"),
                "dexSave": saves.get("dex"),
                "conSave": saves.get("con"),
                "intSave": saves.get("int"),
                "wisSave": saves.get("wis"),
                "chaSave": saves.get("cha"),
                "skills": json.dumps(c.get("skills", {})) if isinstance(c.get("skills"), dict) else str(c.get("skills", "")),
                "damageVulnerabilities": (c.get("damage_vulnerabilities") or ""),
                "damageResistances": (c.get("damage_resistances") or ""),
                "damageImmunities": (c.get("damage_immunities") or ""),
                "conditionImmunities": (c.get("condition_immunities") or ""),
                "senses": (c.get("senses") or ""),
                "languages": (c.get("languages") or ""),
                "traits": traits_arr,
                "actions": actions_arr,
                "bonusActions": extract_generic_actions(c, "bonus_actions"),
                "reactions": reactions_arr,
                "legendaryActions": legendary_arr,
                "legendaryDescription": c.get("legendary_desc", ""),
                "lairActions": None,
                "cr": cr,
                "xp": xp
            })

        print(f"  Page {page}: {len(results)} creatures ({len(entries)} total)")
        if not data.get("next"): break
        page += 1

    entries.sort(key=lambda e: e["name"])
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
    print(f"Wrote {len(entries)} monsters to {OUTPUT_PATH}")

if __name__ == "__main__":
    main()
