#!/usr/bin/env python3
"""Fetch D&D 5.5e SRD 5.2 spells from open5e.com."""

import json, os, sys, time, urllib.request, urllib.error

API_BASE = "https://api.open5e.com"
PAGE_SIZE = 100
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "srd", "srd-5.2-spells.json",
)

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
        url = f"{API_BASE}/v2/spells/?document__key__in=srd-2024&limit={PAGE_SIZE}&page={page}"
        data = api_get(url)
        results = data.get("results", [])
        if not results:
            break
        for s in results:
            components_list = s.get("components", []) or []
            component_names = []
            for comp in components_list:
                if isinstance(comp, dict):
                    component_names.append(comp.get("raw", comp.get("abbreviation", "")))
                else:
                    component_names.append(str(comp))
            components = ", ".join(c for c in component_names if c)
            material = s.get("material_component", "") or ""
            if material:
                components = f"{components} ({material})"
            entries.append({
                "sourceKey": s["key"].removeprefix("srd-2024_"),
                "name": s["name"],
                "level": s.get("level", 0),
                "school": s.get("school_name", ""),
                "castingTime": s.get("casting_time", ""),
                "range": s.get("range_text", ""),
                "components": components,
                "duration": s.get("duration", ""),
                "description": s.get("desc", ""),
                "higherLevel": s.get("higher_level_desc", ""),
                "ritual": s.get("ritual", False),
                "concentration": s.get("requires_concentration", False),
            })
        print(f"  Page {page}: {len(results)} spells ({len(entries)} total)")
        if not data.get("next"): break
        page += 1

    entries.sort(key=lambda s: (s["level"], s["name"]))
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
    print(f"Wrote {len(entries)} spells to {OUTPUT_PATH}")

if __name__ == "__main__":
    main()
