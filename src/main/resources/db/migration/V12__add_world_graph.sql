create table faction (
    id uuid not null,
    campaign_id uuid not null,
    name varchar(500) not null,
    goals CLOB,
    resources CLOB,
    reputation_notes CLOB,
    note_id uuid,
    tags varchar(1000),
    source_locator varchar(500),
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_faction_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_faction_note foreign key (note_id) references note on delete set null
);
create index idx_faction_campaign on faction (campaign_id);

create table world_location (
    id uuid not null,
    campaign_id uuid not null,
    name varchar(500) not null,
    kind varchar(20) not null default 'SITE',
    parent_location_id uuid,
    map_id uuid,
    map_region_key varchar(100),
    note_id uuid,
    summary CLOB,
    services CLOB,
    secrets CLOB,
    tags varchar(1000),
    source_locator varchar(500),
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_world_location_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_world_location_parent foreign key (parent_location_id) references world_location on delete set null,
    constraint fk_world_location_map foreign key (map_id) references game_map on delete set null,
    constraint fk_world_location_note foreign key (note_id) references note on delete set null
);
create index idx_world_location_campaign on world_location (campaign_id);

create table world_npc (
    id uuid not null,
    campaign_id uuid not null,
    name varchar(500) not null,
    role varchar(500),
    disposition varchar(20),
    faction_id uuid,
    location_id uuid,
    note_id uuid,
    statblock_id uuid,
    appearance CLOB,
    voice CLOB,
    motivation CLOB,
    secret CLOB,
    inventory_text CLOB,
    status varchar(20) not null default 'UNKNOWN',
    tags varchar(1000),
    source_locator varchar(500),
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_world_npc_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_world_npc_faction foreign key (faction_id) references faction on delete set null,
    constraint fk_world_npc_location foreign key (location_id) references world_location on delete set null,
    constraint fk_world_npc_note foreign key (note_id) references note on delete set null,
    constraint fk_world_npc_statblock foreign key (statblock_id) references stat_block on delete set null
);
create index idx_world_npc_campaign on world_npc (campaign_id);

create table world_location_encounter (
    location_id uuid not null,
    encounter_id uuid not null,
    sort_order integer not null default 0,
    primary key (location_id, encounter_id),
    constraint fk_wle_location foreign key (location_id) references world_location on delete cascade,
    constraint fk_wle_encounter foreign key (encounter_id) references encounter on delete cascade
);

create table world_location_travel (
    location_id uuid not null,
    target_location_id uuid not null,
    sort_order integer not null default 0,
    primary key (location_id, target_location_id),
    constraint fk_wlt_from foreign key (location_id) references world_location on delete cascade,
    constraint fk_wlt_to foreign key (target_location_id) references world_location on delete cascade,
    constraint ck_wlt_not_self check (location_id <> target_location_id)
);

create table world_relationship (
    id uuid not null,
    campaign_id uuid not null,
    kind varchar(30) not null,
    from_type varchar(30) not null,
    from_id uuid not null,
    to_type varchar(30) not null,
    to_id uuid not null,
    directed boolean not null default true,
    knowledge varchar(20) not null default 'PUBLIC',
    status varchar(20) not null default 'ACTIVE',
    notes CLOB,
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_world_relationship_campaign foreign key (campaign_id) references campaign on delete cascade
);
create index idx_world_relationship_campaign on world_relationship (campaign_id);

create table faction_clock (
    id uuid not null,
    campaign_id uuid not null,
    faction_id uuid not null,
    title varchar(500) not null,
    segments integer not null,
    filled integer not null default 0,
    objective_id uuid,
    scene_id uuid,
    notes CLOB,
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_faction_clock_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_faction_clock_faction foreign key (faction_id) references faction on delete cascade,
    constraint fk_faction_clock_objective foreign key (objective_id) references quest_objective on delete set null,
    constraint fk_faction_clock_scene foreign key (scene_id) references adventure_scene on delete set null,
    constraint ck_faction_clock_segments check (segments >= 1),
    constraint ck_faction_clock_filled check (filled >= 0 and filled <= segments)
);
create index idx_faction_clock_campaign on faction_clock (campaign_id);

alter table scene_participant add column world_npc_id uuid;
alter table scene_participant add constraint fk_scene_participant_world_npc
    foreign key (world_npc_id) references world_npc on delete set null;
