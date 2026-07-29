alter table handout drop constraint if exists ck_handout_derivative_source;
alter table handout drop constraint if exists ck_handout_safety_classification;
alter table handout drop constraint if exists fk_handout_source;

alter table handout drop column if exists derivative_recipe;
alter table handout drop column if exists source_handout_id;
alter table handout drop column if exists safety_classification;
