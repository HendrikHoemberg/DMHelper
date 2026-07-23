create table cockpit_layout_preset (
    id uuid not null,
    name varchar(80) not null,
    normalized_name varchar(80) not null,
    layout_schema_version integer not null,
    layout_json clob not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    primary key (id),
    constraint uq_cockpit_layout_preset_name unique (normalized_name),
    constraint ck_cockpit_layout_preset_name
        check (char_length(trim(name)) between 1 and 80),
    constraint ck_cockpit_layout_preset_schema
        check (layout_schema_version = 1)
);
