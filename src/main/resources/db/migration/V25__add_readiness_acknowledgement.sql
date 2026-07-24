create table readiness_acknowledgement (
    id uuid not null,
    campaign_id uuid not null,
    item_key varchar(200) not null,
    accepted_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uq_readiness_ack_campaign_item unique (campaign_id, item_key)
);
create index idx_readiness_ack_campaign on readiness_acknowledgement (campaign_id);
