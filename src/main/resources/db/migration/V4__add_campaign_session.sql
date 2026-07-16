create table campaign_session (
    id uuid not null,
    campaign_id uuid not null,
    status varchar(16) not null,
    started_at timestamp(6) with time zone,
    paused_at timestamp(6) with time zone,
    review_started_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    start_in_game_year integer,
    start_in_game_month integer,
    start_in_game_day integer,
    plan_note_id uuid,
    workspace_map_id uuid,
    presentation_mode varchar(16) not null,
    presented_map_id uuid,
    presented_handout_id uuid,
    draft_body CLOB,
    version bigint not null,
    primary key (id),
    constraint uq_campaign_session_campaign unique (campaign_id),
    constraint fk_campaign_session_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_campaign_session_plan foreign key (plan_note_id) references note,
    constraint fk_campaign_session_workspace_map foreign key (workspace_map_id) references game_map,
    constraint fk_campaign_session_presented_map foreign key (presented_map_id) references game_map,
    constraint fk_campaign_session_presented_handout foreign key (presented_handout_id) references handout,
    constraint ck_campaign_session_presentation check (
        (presentation_mode = 'CURTAIN' and presented_map_id is null and presented_handout_id is null) or
        (presentation_mode = 'MAP' and presented_map_id is not null and presented_handout_id is null) or
        (presentation_mode = 'HANDOUT' and presented_map_id is null and presented_handout_id is not null)
    )
);

create table campaign_session_attendee (
    session_id uuid not null,
    party_member_id uuid not null,
    primary key (session_id, party_member_id),
    constraint fk_session_attendee_session foreign key (session_id) references campaign_session on delete cascade,
    constraint fk_session_attendee_member foreign key (party_member_id) references party_member
);

create table session_scene_visit (
    id uuid not null,
    session_id uuid not null,
    scene_id uuid not null,
    visited_at timestamp(6) with time zone not null,
    completed_at timestamp(6) with time zone,
    primary key (id),
    constraint uq_session_scene_visit unique (session_id, scene_id),
    constraint fk_session_scene_visit_session foreign key (session_id) references campaign_session on delete cascade,
    constraint fk_session_scene_visit_scene foreign key (scene_id) references adventure_scene
);

create index idx_campaign_session_status_updated on campaign_session (status, updated_at);
create index idx_session_scene_visit_order on session_scene_visit (session_id, visited_at, id);
