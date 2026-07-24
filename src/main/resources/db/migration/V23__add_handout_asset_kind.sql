alter table handout add column asset_kind varchar(24) not null default 'SOURCE_PAGE';

-- Existing imports were captured as generic source-page material; that is the
-- most conservative interpretation and matches the entity default.
update handout set asset_kind = 'SOURCE_PAGE' where asset_kind is null;
