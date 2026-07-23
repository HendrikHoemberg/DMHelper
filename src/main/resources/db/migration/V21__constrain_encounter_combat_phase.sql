alter table encounter
    add constraint ck_encounter_combat_phase
        check (regexp_like(combat_phase, '^(SETUP|RUNNING)$'));
