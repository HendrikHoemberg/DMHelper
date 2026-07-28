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

-- Add SUSPENDED to encounter status enum
alter table encounter alter column status set data type enum('PLANNED', 'ACTIVE', 'SUSPENDED', 'DONE');
