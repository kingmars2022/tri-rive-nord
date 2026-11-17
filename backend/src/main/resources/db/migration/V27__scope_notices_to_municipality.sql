-- --------------------------------------------------------------------------
-- Attach every special notice to the city it was published for.
--
-- V20 scoped collections, holidays, rules, preferences and the sorting guide
-- to a municipality and left notices out, so a "collection postponed" posted
-- for Blainville went to every resident with reminders on, in every city,
-- and the public active-notices list showed every city's notices to all.
--
-- Every notice that exists predates the other cities, so the backfill is
-- Blainville. The column is then NOT NULL: a notice for no city has no
-- audience, and a notice for every city is not something an administrator of
-- one city should be able to publish.
-- --------------------------------------------------------------------------

set @blainville = (select id from municipality where slug = 'blainville');

alter table special_notice add column municipality_id bigint;

update special_notice set municipality_id = @blainville;

alter table special_notice modify column municipality_id bigint not null;

alter table special_notice
    add constraint fk_notice_municipality foreign key (municipality_id) references municipality(id);

-- Every read is "this city's notices": the public list filters on the city and
-- then the date window, the admin list on the city and then sorts by date.
create index idx_special_notice_muni_dates on special_notice (municipality_id, starts_on, ends_on);
