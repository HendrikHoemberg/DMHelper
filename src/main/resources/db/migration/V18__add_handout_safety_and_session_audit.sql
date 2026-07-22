alter table handout add column safety_classification varchar(24) not null default 'UNREVIEWED';
alter table handout add column source_handout_id uuid;
alter table handout add column derivative_recipe CLOB;

update handout
set safety_classification = case when dm_only then 'DM_SOURCE' else 'UNREVIEWED' end;

alter table handout add constraint fk_handout_source
    foreign key (source_handout_id) references handout on delete restrict;
create index idx_handout_source on handout (source_handout_id);

create table session_audit_entry (
    id uuid not null,
    session_id uuid not null,
    entry_type varchar(32) not null,
    content_type varchar(32) not null,
    content_id uuid not null,
    details CLOB not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_session_audit_session foreign key (session_id)
        references campaign_session on delete cascade
);
create index idx_session_audit_order on session_audit_entry (session_id, created_at, id);
