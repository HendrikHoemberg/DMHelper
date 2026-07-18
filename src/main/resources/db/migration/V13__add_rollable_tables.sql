-- V13__add_rollable_tables.sql

create table rollable_table (
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
    address_mode varchar(16) not null,
    roll_expression varchar(255),
    category varchar(16) not null,
    tags varchar(1000),
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_rollable_table_campaign foreign key (campaign_id_fk)
        references campaign on delete cascade
);

create index idx_rollable_table_campaign on rollable_table (campaign_id_fk);

create table rollable_table_entry (
    id uuid not null,
    table_id uuid not null,
    entry_key varchar(255) not null,
    range_start integer,
    range_end integer,
    weight integer,
    result_text CLOB,
    quantity_expression varchar(255),
    sort_order integer not null,
    primary key (id),
    constraint fk_rte_table foreign key (table_id)
        references rollable_table on delete cascade,
    constraint uq_rollable_table_entry_key unique (table_id, entry_key)
);

create index idx_rte_table on rollable_table_entry (table_id);

create table rollable_table_entry_reference (
    id uuid not null,
    entry_id uuid not null,
    target_scope varchar(16) not null,
    target_type varchar(50) not null,
    target_id uuid,
    catalog_ruleset varchar(100),
    catalog_source_key varchar(255),
    display_text varchar(500),
    sort_order integer not null,
    primary key (id),
    constraint fk_rter_entry foreign key (entry_id)
        references rollable_table_entry on delete cascade
);

create index idx_rter_entry on rollable_table_entry_reference (entry_id);

create table world_location_table_link (
    id uuid not null,
    location_id uuid not null,
    table_id uuid not null,
    role varchar(50),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_wltl_location foreign key (location_id)
        references world_location on delete cascade,
    constraint fk_wltl_table foreign key (table_id)
        references rollable_table on delete restrict
);

create index idx_wltl_location on world_location_table_link (location_id);
create index idx_wltl_table on world_location_table_link (table_id);

create table table_roll_log (
    id uuid not null,
    campaign_id uuid not null,
    table_id uuid,
    table_key_snapshot varchar(255),
    table_name_snapshot varchar(500),
    result_json CLOB,
    draft_type varchar(16),
    draft_status varchar(16) not null,
    resolved_target_ids CLOB,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_trl_campaign foreign key (campaign_id)
        references campaign on delete cascade,
    constraint fk_trl_table foreign key (table_id)
        references rollable_table on delete set null
);

create index idx_table_roll_log_campaign on table_roll_log (campaign_id);
create index idx_table_roll_log_table on table_roll_log (table_id);
