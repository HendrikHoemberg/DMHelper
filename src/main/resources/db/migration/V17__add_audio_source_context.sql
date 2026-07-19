-- Preserve the context of accepted, pending, and victory cues across confirm-mode requests.
alter table session_audio_state add column accepted_source_kind varchar(32);
alter table session_audio_state add column accepted_source_id uuid;
alter table session_audio_state add column accepted_source_label varchar(500);
alter table session_audio_state add column pending_source_kind varchar(32);
alter table session_audio_state add column pending_source_id uuid;
alter table session_audio_state add column pending_source_label varchar(500);
alter table session_audio_state add column victory_source_id uuid;
alter table session_audio_state add column victory_source_label varchar(500);
