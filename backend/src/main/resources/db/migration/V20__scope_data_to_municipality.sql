-- --------------------------------------------------------------------------
-- Attach every row that describes a local rule to the municipality it is a
-- rule for. Everything that exists today is Blainville's, so that is the
-- backfill; the columns are made NOT NULL afterwards, because a collection
-- date or a sorting rule belonging to no city is not a state worth allowing.
--
-- `sorting_item` gets a municipality too, with no shared base layer, and that
-- is the decision worth arguing with. A base guide that cities inherit from
-- and override would mean fewer rows, and most entries really are the same in
-- all four. But the premise of this application is that these rules are local
-- -- a greasy pizza box is organics here and garbage in many other cities --
-- so inheriting a rule into a city whose own document was never read is
-- exactly the failure this project exists to avoid: an answer that is
-- confident and wrong. Every row traces to one city's published document.
--
-- `item_key` is what makes the duplication pay for itself: the same logical
-- item carries the same key in every city, so the four can be compared, and
-- a city that disagrees with the others is a query rather than a discovery.
-- --------------------------------------------------------------------------

set @blainville = (select id from municipality where slug = 'blainville');

alter table collection_schedule_rule add column municipality_id bigint;
alter table collection_event        add column municipality_id bigint;
alter table collection_holiday      add column municipality_id bigint;
alter table user_preference         add column municipality_id bigint;
alter table sorting_item            add column municipality_id bigint;

update collection_schedule_rule set municipality_id = @blainville;
update collection_event           set municipality_id = @blainville;
update collection_holiday         set municipality_id = @blainville;
update user_preference            set municipality_id = @blainville;
update sorting_item               set municipality_id = @blainville;

alter table collection_schedule_rule modify column municipality_id bigint not null;
alter table collection_event        modify column municipality_id bigint not null;
alter table collection_holiday      modify column municipality_id bigint not null;
alter table user_preference         modify column municipality_id bigint not null;
alter table sorting_item            modify column municipality_id bigint not null;

alter table collection_schedule_rule
    add constraint fk_rule_municipality     foreign key (municipality_id) references municipality(id);
alter table collection_event
    add constraint fk_event_municipality    foreign key (municipality_id) references municipality(id);
alter table collection_holiday
    add constraint fk_holiday_municipality  foreign key (municipality_id) references municipality(id);
alter table user_preference
    add constraint fk_pref_municipality     foreign key (municipality_id) references municipality(id);
alter table sorting_item
    add constraint fk_item_municipality     foreign key (municipality_id) references municipality(id);

-- The old uniqueness rule said one holiday per date per sector, which would
-- now collide the moment a second city has a holiday on the same date.
alter table collection_holiday drop index uq_collection_holiday_date_sector;
alter table collection_holiday
    add unique key uq_collection_holiday (municipality_id, holiday_date, sector);

-- Reads are always "this city, this date" and "this city, this language",
-- so the municipality belongs at the front of those indexes.
create index idx_collection_event_muni_date on collection_event (municipality_id, collection_date);
create index idx_rule_muni on collection_schedule_rule (municipality_id);
create index idx_sorting_item_muni on sorting_item (municipality_id);

-- A stable identifier per logical item, derived from the English name so the
-- 35 rows already loaded do not need a hand-written mapping.
alter table sorting_item add column item_key varchar(80);

update sorting_item i
join sorting_item_translation t
  on t.item_id = i.id and t.language_code = 'en'
set i.item_key = trim(both '-' from
        left(lower(regexp_replace(t.name, '[^a-zA-Z0-9]+', '-')), 80));

-- Anything without an English name falls back to its id rather than null, so
-- the column can be NOT NULL and the unique key can be trusted.
update sorting_item set item_key = concat('item-', id) where item_key is null or item_key = '';

alter table sorting_item modify column item_key varchar(80) not null;
alter table sorting_item add unique key uq_sorting_item_muni_key (municipality_id, item_key);
