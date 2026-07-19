-- V15__add_audio_cues.sql
-- Additive audio cue and session runtime-state model.

create table audio_cue (
    id uuid not null,
    campaign_id uuid not null,
    cue_key varchar(100) not null,
    name varchar(500) not null,
    provider_id varchar(255),
    reference_kind varchar(16) not null,
    provider_reference varchar(500) not null,
    cached_title varchar(500),
    artist_or_owner varchar(500),
    artwork_url varchar(2000),
    duration_seconds integer,
    category varchar(20) not null,
    volume_hint integer,
    transition_preference varchar(16) not null,
    notes CLOB,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint fk_audio_cue_campaign foreign key (campaign_id)
        references campaign on delete cascade,
    constraint uq_audio_cue_campaign_key unique (campaign_id, cue_key),
    constraint ck_audio_cue_volume check (volume_hint is null or (volume_hint >= 0 and volume_hint <= 100)),
    constraint ck_audio_cue_duration check (duration_seconds is null or duration_seconds >= 0)
);

create index idx_audio_cue_campaign on audio_cue (campaign_id);
create index idx_audio_cue_name on audio_cue (name);
create index idx_audio_cue_provider_reference on audio_cue (reference_kind, provider_reference);

create table session_audio_state (
    id uuid not null,
    session_id uuid not null,
    manual_override_cue_id uuid,
    accepted_automatic_cue_id uuid,
    pending_cue_id uuid,
    dismissed_candidate_cue_id uuid,
    muted boolean not null default false,
    temporary_victory_cue_id uuid,
    victory_until timestamp with time zone,
    version bigint not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint fk_sas_session foreign key (session_id)
        references campaign_session on delete cascade,
    constraint uq_session_audio_state_session unique (session_id),
    constraint fk_sas_manual_override foreign key (manual_override_cue_id)
        references audio_cue on delete set null,
    constraint fk_sas_accepted_automatic foreign key (accepted_automatic_cue_id)
        references audio_cue on delete set null,
    constraint fk_sas_pending foreign key (pending_cue_id)
        references audio_cue on delete set null,
    constraint fk_sas_dismissed foreign key (dismissed_candidate_cue_id)
        references audio_cue on delete set null,
    constraint fk_sas_victory foreign key (temporary_victory_cue_id)
        references audio_cue on delete set null
);

alter table campaign add column default_audio_cue_id uuid;
alter table campaign add constraint fk_campaign_default_audio_cue
    foreign key (default_audio_cue_id) references audio_cue on delete restrict;

alter table adventure_scene add column scene_audio_cue_id uuid;
alter table adventure_scene add constraint fk_scene_audio_cue
    foreign key (scene_audio_cue_id) references audio_cue on delete restrict;
create index idx_scene_audio_cue on adventure_scene (scene_audio_cue_id);

alter table encounter add column combat_audio_cue_id uuid;
alter table encounter add column victory_audio_cue_id uuid;
alter table encounter add column victory_cue_duration_seconds integer;
alter table encounter add constraint fk_encounter_combat_audio_cue
    foreign key (combat_audio_cue_id) references audio_cue on delete restrict;
alter table encounter add constraint fk_encounter_victory_audio_cue
    foreign key (victory_audio_cue_id) references audio_cue on delete restrict;
create index idx_encounter_combat_audio_cue on encounter (combat_audio_cue_id);
create index idx_encounter_victory_audio_cue on encounter (victory_audio_cue_id);

alter table world_location add column location_audio_cue_id uuid;
alter table world_location add constraint fk_world_location_audio_cue
    foreign key (location_audio_cue_id) references audio_cue on delete restrict;
create index idx_world_location_audio_cue on world_location (location_audio_cue_id);
