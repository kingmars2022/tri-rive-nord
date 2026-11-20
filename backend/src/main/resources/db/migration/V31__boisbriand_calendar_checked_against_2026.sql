-- --------------------------------------------------------------------------
-- Boisbriand: the calendar V25 projected from 2025, checked against the
-- city's own 2026 calendar.
--
-- Source: "Calendrier des collectes 2026" (4 pages, created 2025-11-26),
-- archived as data/municipal-sources/boisbriand/2026-11-20/. It arrived after
-- V25 went live, which had carried the 2025 calendar's pattern forward a
-- fortnight at a time. Read against it:
--
--   * Every date V25 produces from 17 November to 31 December 2026 is the
--     one printed, in both sectors and on every weekday: recycling in the
--     weeks of 23 November, 7 and 21 December; garbage with it in sector A
--     and in the other weeks in sector B; organics weekly through the week
--     of 30 November, then in the weeks of 14 and 28 December; collections
--     due 25 December and 1 January 2027 moved to the 24th and the 31st; the
--     excess-waste week of 15-18 December. The pattern is the same for the
--     whole of 2026, so the 2025 phase held.
--   * Page 2's street table matches V26 street for street: the same 252
--     names and civic ranges, each with the same sector and weekday.
--
-- So no date changes. What was wrong is what residents are told: every note
-- says the schedule was worked out from the 2025 calendar. This splits each
-- rule at the end of what the 2026 calendar prints (the occurrence due
-- 1 January 2027, which it moves to 31 December), so that:
--
--   * up to then, the note says the date is the 2026 calendar's, and the
--     source is that PDF;
--   * after it, the note says the same rhythm is carried on until the city
--     publishes 2027, and still points to boisbriand.ca.
--
-- The street table and sectors point to the 2026 PDF as their source.
-- --------------------------------------------------------------------------

select set_config('app.bb', ((select id from municipality where slug = 'boisbriand'))::text, false);
select set_config('app.pdf',
    'https://www.ville.boisbriand.qc.ca/storage/app/media/services/citoyens/collectes-des-matieres-residuelles/CalendrierCollectes_2026.pdf',
    false);

-- The caveat V25 appended to every note, and what replaces it on each side
-- of the split.
select set_config('app.old_fr', ' Horaire etabli d''apres le calendrier 2025 de la Ville; verifiez votre journee sur boisbriand.ca.', false);
select set_config('app.old_en', ' Schedule worked out from the city''s 2025 calendar; check your day on boisbriand.ca.', false);
select set_config('app.old_zh', '（根据市政府 2025 年日历推算，请在 boisbriand.ca 核对。）', false);
select set_config('app.y26_fr', ' D''apres le calendrier 2026 de la Ville.', false);
select set_config('app.y26_en', ' From the city''s 2026 calendar.', false);
select set_config('app.y26_zh', '（依据市政府 2026 年日历。）', false);
select set_config('app.y27_fr', ' Prolonge au rythme du calendrier 2026 de la Ville en attendant celui de 2027; verifiez votre journee sur boisbriand.ca.', false);
select set_config('app.y27_en', ' Continued at the rhythm of the city''s 2026 calendar until the 2027 one is out; check your day on boisbriand.ca.', false);
select set_config('app.y27_zh', '（按市政府 2026 年日历的节奏延续，2027 年日历公布前请在 boisbriand.ca 核对。）', false);

-- 1. A 2027 continuation of every rule that runs past 1 January 2027, from
--    its first occurrence after that day, same rhythm and same end. It
--    carries the original's generated_through, so the occurrences already
--    generated are not generated again.
insert into collection_schedule_rule
(municipality_id, sector, collection_type, bin_color, anchor_date, interval_weeks, valid_until,
 generated_through, note_fr, note_en, note_zh, source_url, active)
select municipality_id, sector, collection_type, bin_color,
       anchor_date + 7 * interval_weeks
           * ceil((date '2027-01-02' - anchor_date)::numeric / (7 * interval_weeks))::integer,
       interval_weeks, valid_until, generated_through,
       replace(note_fr, current_setting('app.old_fr'), current_setting('app.y27_fr')),
       replace(note_en, current_setting('app.old_en'), current_setting('app.y27_en')),
       replace(note_zh, current_setting('app.old_zh'), current_setting('app.y27_zh')),
       source_url, active
from collection_schedule_rule
where municipality_id = current_setting('app.bb')::bigint
  and anchor_date <= date '2027-01-01'
  and (valid_until is null or valid_until > date '2027-01-01');

-- 2. The originals end where the 2026 calendar does.
update collection_schedule_rule
set valid_until = date '2027-01-01'
where municipality_id = current_setting('app.bb')::bigint
  and anchor_date <= date '2027-01-01'
  and (valid_until is null or valid_until > date '2027-01-01');

-- 3. Rules inside what the 2026 calendar prints: its words and its PDF.
update collection_schedule_rule
set note_fr = replace(note_fr, current_setting('app.old_fr'), current_setting('app.y26_fr')),
    note_en = replace(note_en, current_setting('app.old_en'), current_setting('app.y26_en')),
    note_zh = replace(note_zh, current_setting('app.old_zh'), current_setting('app.y26_zh')),
    source_url = current_setting('app.pdf')
where municipality_id = current_setting('app.bb')::bigint
  and valid_until <= date '2027-01-01';

-- 4. Rules that start after it (V25's April 2027 organics): the carried-on
--    caveat.
update collection_schedule_rule
set note_fr = replace(note_fr, current_setting('app.old_fr'), current_setting('app.y27_fr')),
    note_en = replace(note_en, current_setting('app.old_en'), current_setting('app.y27_en')),
    note_zh = replace(note_zh, current_setting('app.old_zh'), current_setting('app.y27_zh'))
where municipality_id = current_setting('app.bb')::bigint
  and anchor_date > date '2027-01-01';

-- 5. Collections already generated carry their rule's note (with a holiday
--    explanation around it where one moved them). The occurrence due
--    1 January 2027 is stored on 31 December, which is where the 2026
--    calendar prints it.
update collection_event
set note_fr = replace(note_fr, current_setting('app.old_fr'), current_setting('app.y26_fr')),
    note_en = replace(note_en, current_setting('app.old_en'), current_setting('app.y26_en')),
    note_zh = replace(note_zh, current_setting('app.old_zh'), current_setting('app.y26_zh')),
    source_url = current_setting('app.pdf')
where municipality_id = current_setting('app.bb')::bigint
  and auto_generated = true
  and collection_date <= date '2026-12-31';

update collection_event
set note_fr = replace(note_fr, current_setting('app.old_fr'), current_setting('app.y27_fr')),
    note_en = replace(note_en, current_setting('app.old_en'), current_setting('app.y27_en')),
    note_zh = replace(note_zh, current_setting('app.old_zh'), current_setting('app.y27_zh'))
where municipality_id = current_setting('app.bb')::bigint
  and auto_generated = true
  and collection_date > date '2026-12-31';

-- 6. What the 2026 calendar also prints: the excess-waste week, the two
--    holidays, the sectors and the street table.
update collection_event
set source_url = current_setting('app.pdf')
where municipality_id = current_setting('app.bb')::bigint
  and collection_type = 'bulky'
  and collection_date between date '2026-01-01' and date '2026-12-31';

update collection_holiday
set source_url = current_setting('app.pdf')
where municipality_id = current_setting('app.bb')::bigint
  and holiday_date in (date '2026-12-25', date '2027-01-01');

update municipality_sector
set source_url = current_setting('app.pdf')
where municipality_id = current_setting('app.bb')::bigint;

update municipality_street
set source_url = current_setting('app.pdf')
where municipality_id = current_setting('app.bb')::bigint;
