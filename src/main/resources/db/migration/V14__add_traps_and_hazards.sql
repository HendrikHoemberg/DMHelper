-- V14__add_traps_and_hazards.sql
-- Additive trap/hazard compendium model. Does not transform existing prose.

create table trap (
    id uuid not null,
    source_key varchar(255) not null,
    source varchar(10) not null,
    campaign_id_fk uuid,
    prov_source_title varchar(255),
    prov_edition_version varchar(100),
    prov_source_locator varchar(500),
    prov_license varchar(30),
    prov_imported_at timestamp(6) with time zone,
    prov_converter_id varchar(100),
    prov_converter_version varchar(50),
    prov_source_hash varchar(128),
    prov_confidence varchar(20),
    name varchar(500) not null,
    description CLOB,
    severity varchar(16) not null,
    min_level integer,
    max_level integer,
    created_at timestamp with time zone not null,
    trigger_description CLOB,
    trigger_area_hint varchar(1000),
    detection_passive_threshold integer,
    detection_mode varchar(10),
    detection_ability varchar(50),
    detection_skill varchar(50),
    detection_dc integer,
    attack_bonus integer,
    save_mode varchar(10),
    save_ability varchar(50),
    save_skill varchar(50),
    save_dc integer,
    damage_expression varchar(255),
    additional_effect CLOB,
    reset_mode varchar(16) not null,
    reset_timing varchar(500),
    statblock_id uuid,
    countermeasure_notes CLOB,
    primary key (id),
    constraint fk_trap_campaign foreign key (campaign_id_fk)
        references campaign on delete cascade,
    constraint fk_trap_statblock foreign key (statblock_id)
        references stat_block on delete set null
);

create index idx_trap_campaign on trap (campaign_id_fk);
create index idx_trap_name on trap (name);
create index idx_trap_statblock on trap (statblock_id);

create table hazard (
    id uuid not null,
    source_key varchar(255) not null,
    source varchar(10) not null,
    campaign_id_fk uuid,
    prov_source_title varchar(255),
    prov_edition_version varchar(100),
    prov_source_locator varchar(500),
    prov_license varchar(30),
    prov_imported_at timestamp(6) with time zone,
    prov_converter_id varchar(100),
    prov_converter_version varchar(50),
    prov_source_hash varchar(128),
    prov_confidence varchar(20),
    name varchar(500) not null,
    description CLOB,
    severity varchar(16) not null,
    min_level integer,
    max_level integer,
    created_at timestamp with time zone not null,
    exposure_mode varchar(20) not null,
    exposure_text CLOB,
    area_hint varchar(1000),
    check_mode varchar(10),
    check_ability varchar(50),
    check_skill varchar(50),
    check_dc integer,
    damage_expression varchar(255),
    escalation_text CLOB,
    ending_conditions CLOB,
    primary key (id),
    constraint fk_hazard_campaign foreign key (campaign_id_fk)
        references campaign on delete cascade
);

create index idx_hazard_campaign on hazard (campaign_id_fk);
create index idx_hazard_name on hazard (name);

create table trap_disarm_method (
    id uuid not null,
    trap_id uuid not null,
    method_key varchar(255) not null,
    label varchar(500) not null,
    ability varchar(50),
    skill varchar(50),
    tool varchar(100),
    dc integer,
    failure_consequence CLOB,
    sort_order integer not null,
    primary key (id),
    constraint fk_trap_disarm_method_trap foreign key (trap_id)
        references trap on delete cascade,
    constraint uq_trap_disarm_method_key unique (trap_id, method_key)
);

create index idx_trap_disarm_method_trap on trap_disarm_method (trap_id);

create table trap_damage_type (
    trap_id uuid not null,
    damage_type varchar(20) not null,
    sort_order integer not null,
    primary key (trap_id, sort_order),
    constraint fk_trap_damage_type_trap foreign key (trap_id)
        references trap on delete cascade
);

create table hazard_damage_type (
    hazard_id uuid not null,
    damage_type varchar(20) not null,
    sort_order integer not null,
    primary key (hazard_id, sort_order),
    constraint fk_hazard_damage_type_hazard foreign key (hazard_id)
        references hazard on delete cascade
);

create table threat_reference (
    id uuid not null,
    trap_id uuid,
    hazard_id uuid,
    role varchar(20) not null,
    target_type varchar(50) not null,
    target_id uuid,
    display_text varchar(500),
    sort_order integer not null,
    primary key (id),
    constraint fk_threat_reference_trap foreign key (trap_id)
        references trap on delete cascade,
    constraint fk_threat_reference_hazard foreign key (hazard_id)
        references hazard on delete cascade,
    constraint ck_threat_reference_owner check (
        (trap_id is not null and hazard_id is null)
        or (trap_id is null and hazard_id is not null)
    )
);

create index idx_threat_reference_trap on threat_reference (trap_id);
create index idx_threat_reference_hazard on threat_reference (hazard_id);
create index idx_threat_reference_target on threat_reference (target_type, target_id);

create table map_threat_pin (
    id uuid not null,
    map_id uuid not null,
    pin_key varchar(255) not null,
    threat_kind varchar(10) not null,
    threat_id uuid not null,
    x_px integer not null,
    y_px integer not null,
    label varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_map_threat_pin_map foreign key (map_id)
        references game_map on delete cascade,
    constraint uq_map_threat_pin_key unique (map_id, pin_key)
);

create index idx_map_threat_pin_map on map_threat_pin (map_id);
create index idx_map_threat_pin_threat on map_threat_pin (threat_kind, threat_id);

alter table scene_section add column threat_kind varchar(10);
alter table scene_section add column threat_id uuid;
create index idx_scene_section_threat on scene_section (threat_kind, threat_id);

alter table combatant add column threat_kind varchar(10);
alter table combatant add column threat_id uuid;
create index idx_combatant_threat on combatant (threat_kind, threat_id);
