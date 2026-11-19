-- --------------------------------------------------------------------------
-- The uniqueness rule still said one collection per (date, sector, type),
-- which was right while there was one city and silently wrong with four.
--
-- Silently is the word that matters. Every city collects organics weekly on
-- a weekday, with sector 'all', so Blainville and Rosemere both having an
-- organics pickup on the same Thursday is the normal case, not an edge one.
-- The generated insert ends in `on duplicate key update collection_event.id
-- = collection_event.id`, which exists to make the calendar top-up
-- repeatable -- so the second city's event would not raise an error. It
-- would be discarded, and the city would show a Thursday with no collection
-- on it. A missing pickup that no exception announces is precisely the
-- defect class this calendar has produced before.
-- --------------------------------------------------------------------------

alter table collection_event drop constraint uq_collection_event_date_sector_type;

alter table collection_event
    add constraint uq_collection_event unique (municipality_id, collection_date, sector, collection_type);
