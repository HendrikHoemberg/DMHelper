create table encounter_token_placement (
    id uuid not null primary key,
    encounter_id uuid not null,
    combatant_id uuid not null,
    map_id uuid not null,
    position_x integer not null,
    position_y integer not null,
    size_cols integer not null,
    size_rows integer not null,
    color varchar(7) not null,
    icon varchar(100),
    constraint uq_encounter_placement_combatant unique (combatant_id),
    constraint fk_encounter_placement_encounter foreign key (encounter_id)
        references encounter(id) on delete cascade,
    constraint fk_encounter_placement_combatant foreign key (combatant_id)
        references combatant(id) on delete cascade,
    constraint fk_encounter_placement_map foreign key (map_id)
        references game_map(id) on delete cascade
);

-- Backfill placements from existing combatant→token links
insert into encounter_token_placement (
    id, encounter_id, combatant_id, map_id,
    position_x, position_y, size_cols, size_rows, color, icon
)
select RANDOM_UUID(), c.encounter_id, c.id, t.map_id,
       t.positionx, t.positiony, t.size_cols, t.size_rows, t.color, t.icon
from combatant c
join token t on t.id = c.token_id
;

-- Add SUSPENDED to encounter status (portable varchar approach)
alter table encounter alter column status varchar(16);
alter table encounter add constraint ck_encounter_status
    check (regexp_like(status, '^(PLANNED|ACTIVE|SUSPENDED|DONE)$'));
