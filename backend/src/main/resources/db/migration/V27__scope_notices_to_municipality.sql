-- --------------------------------------------------------------------------
-- A city notice belongs to a city.
--
-- V20 attached collections, holidays, rules, preferences and the sorting
-- guide to a municipality and left special_notice out, so a notice stayed
-- global: the public "active notices" list showed every city's notices to
-- everyone, and a new notice was fanned out to the inbox - and the phone - of
-- every resident with reminders on, in every city. With one city that was
-- invisible. With Sainte-Therese and Boisbriand loaded, a Blainville
-- "collection postponed" reaches residents whose collection was not.
--
-- Every existing notice predates the other cities, so the backfill is
-- Blainville, the same reasoning V20 used for everything else.
-- --------------------------------------------------------------------------

set @blainville = (select id from municipality where slug = 'blainville');

alter table special_notice add column municipality_id bigint;
update special_notice set municipality_id = @blainville;
alter table special_notice modify column municipality_id bigint not null;
alter table special_notice
    add constraint fk_notice_municipality foreign key (municipality_id) references municipality(id);

-- Reads are "this city's notices in effect today" and "this city's notices,
-- newest first", so the city leads the index.
create index idx_notice_muni_starts on special_notice (municipality_id, starts_on);
