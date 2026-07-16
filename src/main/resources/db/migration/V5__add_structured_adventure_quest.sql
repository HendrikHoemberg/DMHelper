-- New columns on adventure_scene
alter table adventure_scene add column summary varchar(2000);
alter table adventure_scene add column source_locator varchar(500);
alter table adventure_scene add column tags varchar(1000);
alter table adventure_scene add column map_region_key varchar(100);

-- scene_section
create table scene_section (
    id uuid not null,
    scene_id uuid not null,
    kind varchar(20) not null,
    label varchar(500),
    body CLOB,
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint uq_scene_section_scene_sort unique (scene_id, sort_order),
    constraint fk_scene_section_scene foreign key (scene_id) references adventure_scene on delete cascade
);

-- scene_check
create table scene_check (
    id uuid not null,
    scene_id uuid not null,
    label varchar(500),
    ability varchar(50),
    skill varchar(50),
    dc integer,
    visibility varchar(20),
    success CLOB,
    failure CLOB,
    partial CLOB,
    rule_scope varchar(20),
    rule_ruleset varchar(100),
    rule_source_key varchar(100),
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint uq_scene_check_scene_sort unique (scene_id, sort_order),
    constraint fk_scene_check_scene foreign key (scene_id) references adventure_scene on delete cascade
);

-- scene_participant
create table scene_participant (
    id uuid not null,
    scene_id uuid not null,
    display_name varchar(500),
    quantity integer not null default 1,
    disposition varchar(20),
    placement_hint varchar(1000),
    statblock_id uuid,
    note_id uuid,
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_scene_participant_scene foreign key (scene_id) references adventure_scene on delete cascade,
    constraint fk_scene_participant_statblock foreign key (statblock_id) references stat_block on delete set null,
    constraint fk_scene_participant_note foreign key (note_id) references note on delete set null
);

-- scene_transition
create table scene_transition (
    id uuid not null,
    scene_id uuid not null,
    kind varchar(20) not null,
    label varchar(500),
    target_scene_id uuid,
    external_destination varchar(500),
    condition varchar(2000),
    dm_note CLOB,
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint uq_scene_transition_scene_sort unique (scene_id, sort_order),
    constraint fk_scene_transition_scene foreign key (scene_id) references adventure_scene on delete cascade,
    constraint fk_scene_transition_target foreign key (target_scene_id) references adventure_scene on delete set null,
    constraint ck_scene_transition_target check (target_scene_id is null or external_destination is null)
);

-- scene_link
create table scene_link (
    id uuid not null,
    scene_id uuid not null,
    role varchar(30) not null,
    target_scope varchar(20) not null,
    target_type varchar(30) not null,
    target_id uuid not null,
    catalog_ruleset varchar(100),
    catalog_source_key varchar(100),
    display_text varchar(500),
    condition varchar(2000),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_scene_link_scene foreign key (scene_id) references adventure_scene on delete cascade
);

-- quest
create table quest (
    id uuid not null,
    campaign_id uuid not null,
    title varchar(500) not null,
    status varchar(20) not null default 'NOT_STARTED',
    summary CLOB,
    source_locator varchar(500),
    tags varchar(1000),
    rewards CLOB,
    prerequisites CLOB,
    outcome_notes CLOB,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_quest_campaign foreign key (campaign_id) references campaign on delete cascade
);

-- quest_objective
create table quest_objective (
    id uuid not null,
    quest_id uuid not null,
    title varchar(500) not null,
    description CLOB,
    status varchar(20) not null default 'NOT_STARTED',
    completion_mode varchar(20) not null default 'ALL',
    sort_order integer not null default 0,
    source_locator varchar(500),
    primary key (id),
    constraint uq_quest_objective_quest_sort unique (quest_id, sort_order),
    constraint fk_quest_objective_quest foreign key (quest_id) references quest on delete cascade
);

-- quest_objective_dependency
create table quest_objective_dependency (
    quest_id uuid not null,
    objective_id uuid not null,
    prerequisite_objective_id uuid not null,
    primary key (quest_id, objective_id, prerequisite_objective_id),
    constraint fk_quest_dependency_quest foreign key (quest_id) references quest on delete cascade,
    constraint fk_quest_dependency_objective foreign key (objective_id) references quest_objective on delete cascade,
    constraint fk_quest_dependency_prereq foreign key (prerequisite_objective_id) references quest_objective on delete cascade
);

-- quest_link
create table quest_link (
    id uuid not null,
    quest_id uuid not null,
    role varchar(30) not null,
    target_scope varchar(20) not null,
    target_type varchar(30) not null,
    target_id uuid not null,
    catalog_ruleset varchar(100),
    catalog_source_key varchar(100),
    display_text varchar(500),
    condition varchar(2000),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_quest_link_quest foreign key (quest_id) references quest on delete cascade
);

-- source_annotation
create table source_annotation (
    id uuid not null,
    campaign_id uuid not null,
    owner_type varchar(30) not null,
    owner_id uuid not null,
    field_path varchar(200),
    message varchar(1000) not null,
    confidence varchar(20) not null default 'UNKNOWN',
    source_locator varchar(500),
    status varchar(20) not null default 'OPEN',
    resolution_note CLOB,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_source_annotation_campaign foreign key (campaign_id) references campaign on delete cascade
);

create index idx_source_annotation_owner on source_annotation (campaign_id, owner_type, owner_id);

-- session_objective_change
create table session_objective_change (
    id uuid not null,
    session_id uuid not null,
    objective_id uuid not null,
    previous_status varchar(20),
    new_status varchar(20) not null,
    changed_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_session_obj_change_session foreign key (session_id) references campaign_session on delete cascade,
    constraint fk_session_obj_change_objective foreign key (objective_id) references quest_objective on delete cascade
);

create index idx_session_obj_change on session_objective_change (session_id, changed_at, id);
