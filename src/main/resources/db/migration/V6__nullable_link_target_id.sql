-- Catalog-scoped scene/quest links have no package UUID target; package links resolve later.
-- Plan item 6 required nullable target columns for polymorphic package/catalog links.
alter table scene_link alter column target_id drop not null;
alter table quest_link alter column target_id drop not null;
