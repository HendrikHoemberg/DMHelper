create table encounter_token_placement (
    id uuid not null,
    encounter_id uuid not null,
    combatant_id uuid not null,
    map_id uuid not null,
    position_x integer not null,
    position_y integer not null,
    size_cols integer default 1 not null,
    size_rows integer default 1 not null,
    color varchar(7) default '#7b68ee' not null,
    icon varchar(100),
    primary key (id),
    constraint uq_encounter_placement_combatant unique (combatant_id)
);

create index idx_encounter_placement_encounter on encounter_token_placement (encounter_id);
create index idx_encounter_placement_map on encounter_token_placement (map_id);

-- Add SUSPENDED to encounter status enum
alter table encounter alter column status set data type enum('PLANNED', 'ACTIVE', 'SUSPENDED', 'DONE');

-- Add ON DELETE CASCADE to combatant FK so deleting an encounter cascades
alter table combatant drop constraint if exists FKL35UYX5MOSYBN2BT3CGY7I7HK;
alter table combatant add constraint fk_combatant_encounter
    foreign key (encounter_id) references encounter on delete cascade;
