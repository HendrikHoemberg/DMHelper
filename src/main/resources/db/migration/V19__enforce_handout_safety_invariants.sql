update handout
set safety_classification = 'UNREVIEWED',
    source_handout_id = null,
    derivative_recipe = null,
    dm_only = true,
    presented = false
where safety_classification not in ('DM_SOURCE', 'PLAYER_SAFE', 'PLAYER_DERIVATIVE', 'UNREVIEWED')
   or (safety_classification = 'PLAYER_DERIVATIVE'
       and (source_handout_id is null or derivative_recipe is null));

update handout
set source_handout_id = null,
    derivative_recipe = null
where safety_classification <> 'PLAYER_DERIVATIVE';

update handout
set dm_only = case
    when safety_classification in ('PLAYER_SAFE', 'PLAYER_DERIVATIVE') then false
    else true
end;

update handout
set presented = false
where safety_classification in ('DM_SOURCE', 'UNREVIEWED');

alter table handout add constraint ck_handout_safety_classification
    check (regexp_like(safety_classification,
        '^(DM_SOURCE|PLAYER_SAFE|PLAYER_DERIVATIVE|UNREVIEWED)$'));

alter table handout add constraint ck_handout_derivative_source
    check (
        (safety_classification = 'PLAYER_DERIVATIVE'
            and source_handout_id is not null
            and derivative_recipe is not null)
        or
        (safety_classification <> 'PLAYER_DERIVATIVE'
            and source_handout_id is null
            and derivative_recipe is null)
    );

alter table session_audit_entry add constraint ck_session_audit_type
    check (entry_type = 'PRESENTATION_OVERRIDE');
