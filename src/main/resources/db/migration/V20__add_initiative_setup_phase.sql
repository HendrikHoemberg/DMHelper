alter table encounter
    add column combat_phase varchar(16) not null default 'SETUP';

update encounter
set combat_phase = case
    when status = 'DONE' or active_turn_index >= 0 or round > 1 then 'RUNNING'
    else 'SETUP'
end;

alter table combatant
    alter column initiative drop not null;

update combatant
set initiative = null
where initiative = 0
  and encounter_id in (
      select id from encounter where combat_phase = 'SETUP'
  );
