-- V16__add_session_audio_switch_mode.sql
-- Add switch_mode column to support AUTOMATIC/CONFIRM switching.

alter table session_audio_state add column switch_mode varchar(16) not null default 'AUTOMATIC';
