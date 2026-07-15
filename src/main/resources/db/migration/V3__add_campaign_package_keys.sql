create table campaign_package_key (
    id uuid not null,
    campaign_id uuid not null,
    entity_type varchar(40) not null,
    entity_id uuid not null,
    package_key varchar(100) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_package_key_campaign foreign key (campaign_id)
        references campaign on delete cascade,
    constraint uq_package_key_entity unique (campaign_id, entity_type, entity_id),
    constraint uq_package_key_value unique (campaign_id, entity_type, package_key)
);

create index idx_package_key_campaign_type
    on campaign_package_key (campaign_id, entity_type);
