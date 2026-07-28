-- Remove legacy combatant-token link and duplicated combat state
-- V26 already copied all combatant.token_id links into encounter_token_placement

alter table combatant drop constraint if exists FKsi7tk9ad9t0cjv96mm0u72dyh;

delete from token
where id in (
    select distinct token_id from combatant where token_id is not null
);

alter table combatant drop column token_id;

alter table token drop column current_hp;
alter table token drop column max_hp;
alter table token drop column dead;
