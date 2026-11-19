-- --------------------------------------------------------------------------
-- Sainte-Therese's collection calendar, read from the city's own documents.
--
-- Sources, all on www.sainte-therese.ca and archived byte for byte, with
-- their sha256, under data/municipal-sources/sainte-therese/2026-11-17/:
--
--   * "Calendrier des collectes 2026", one PDF per zone (A, B, C, D). Every
--     date below was read off those month grids, where a black bin is
--     ordures menageres, a blue bin is matieres recyclables, a brown bin is
--     matieres organiques and a purple sofa is encombrants.
--   * The "Collectes et ecocentre" page, which states the standing rules the
--     grids are drawn from:
--       - "La collecte du bac bleu s'effectue du mardi au vendredi (selon la
--         zone), une semaine sur deux, en alternance avec le bac noir, tout
--         au long de l'annee." (and the same sentence for the black bin)
--       - "La collecte du bac brun s'effectue tous les lundis d'avril a
--         novembre et un lundi sur deux de decembre a mars, pour toutes les
--         zones."
--
-- What the documents say about how this city is organised, which is not how
-- Blainville is:
--
-- 1. FOUR ZONES, NOT TWO HALVES. A, B, C and D, each with its own weekday:
--    Tuesday, Wednesday, Thursday, Friday. The city defines them only by an
--    address search and a map, never in words, so they are named by letter
--    and nothing more is claimed about where their borders run.
--
-- 2. ORGANICS CHANGE FREQUENCY WITH THE SEASON. Weekly from April to
--    November, every other week from December to March. A rule that is
--    anchor + n weeks for ever cannot say that, which is why this migration
--    gives rules an end date (valid_until) and expresses the season as
--    consecutive rules. The 2026 grids agree: Mondays 2, 9, 16, 23 and 30
--    November, then 14 and 28 December, with nothing on 7 or 21 December.
--
-- 3. THE CITY MOVES A HOLIDAY COLLECTION EARLIER, NOT LATER. Christmas Day
--    and New Year's Day 2027 are both Fridays, zone D's day. The zone D grid
--    prints those collections on Thursday 24 and Thursday 31 December, and
--    nothing on Friday 25. collection_holiday could only move a collection
--    forward, so shift_days now accepts -7..-1 as well as 1..7.
--
--    The same calendar moved 1 January 2026 the other way, from Thursday to
--    Friday 2 January in zone C. That date is past and is not loaded; it is
--    mentioned because it means the direction is the city's choice per
--    holiday, not a rule this table can derive.
--
-- 4. HOLIDAYS MOSTLY DO NOT MOVE ANYTHING. Good Friday, Easter Monday, the
--    Journee nationale des patriotes, Labour Day and Thanksgiving all carry
--    an ordinary collection on the 2026 grids. Only the two Fridays above are
--    seeded, because they are the only shifts printed that are still ahead.
--
-- What this migration does NOT know: the 2027 calendar, which the city has
-- not published. The rules continue into 2027 because the city's page states
-- them as standing rules ("tout au long de l'annee", "tous les lundis d'avril
-- a novembre"), and the 2025-to-2026 boundary on the grids continues the same
-- fortnight. But which 2027 holidays move a collection is printed nowhere
-- yet, so none are claimed, and the organics rules stop at the end of
-- November 2027 rather than guess the winter after. When the 2027 calendar
-- appears it must be read against these rules, not assumed to match them.
--
-- The sorting guide is a separate document and is not loaded here, so
-- guide_verified stays false.
-- --------------------------------------------------------------------------

-- --------------------------------------------------------------------------
-- 1. Schema: rules that end, and holidays that move a collection earlier.
-- --------------------------------------------------------------------------

-- Null means the pattern has no known end, which is what every existing rule
-- is. Inclusive: a rule valid until a date may still fall on that date.
alter table collection_schedule_rule add column valid_until date null;

-- V14 bounded shift_days to 1..7 so the shift loop could not run for ever on
-- a zero. The bound against zero stays; the sign no longer matters, because
-- HolidayShift caps the number of steps regardless of direction. MySQL named
-- V14's unnamed constraint collection_holiday_chk_1.
alter table collection_holiday drop constraint collection_holiday_chk_1;
alter table collection_holiday add constraint chk_collection_holiday_shift
    check (shift_days between -7 and 7 and shift_days <> 0);

-- --------------------------------------------------------------------------
-- 2. The city, its zones, and where they came from.
-- --------------------------------------------------------------------------

select set_config('app.st', ((select id from municipality where slug = 'sainte-therese'))::text, false);
select set_config('app.page', 'https://www.sainte-therese.ca/services/services-aux-citoyens/collectes-et-ecocentre', false);
select set_config('app.pdf', 'https://www.sainte-therese.ca/storage/app/media/services/citoyens/collectes-et-ecocentre/', false);

update municipality set source_url = current_setting('app.page') where id = current_setting('app.st')::bigint;

insert into municipality_sector
(municipality_id, code, position, name_fr, name_en, name_zh, source_url) values
(current_setting('app.st')::bigint, 'all', 0, 'Tout le territoire', 'Whole city', '全市', current_setting('app.page')),
(current_setting('app.st')::bigint, 'a',   1, 'Zone A', 'Zone A', 'A 区', concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneA.pdf')),
(current_setting('app.st')::bigint, 'b',   2, 'Zone B', 'Zone B', 'B 区', concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneB.pdf')),
(current_setting('app.st')::bigint, 'c',   3, 'Zone C', 'Zone C', 'C 区', concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneC.pdf')),
(current_setting('app.st')::bigint, 'd',   4, 'Zone D', 'Zone D', 'D 区', concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneD.pdf'));

-- --------------------------------------------------------------------------
-- 3. Garbage and recycling: alternating fortnights, one weekday per zone.
--
-- Each anchor is the zone's first collection of that kind printed on its
-- grid on or after 17 November 2026, the day this calendar was read. It has
-- to be the first: a rule is never projected backwards from its anchor, so
-- anchoring zone A's recycling on 1 December - the first draft of this file
-- did - silently drops the collection on Tuesday 17 November.
--   A (Tuesday)   garbage 24 Nov, recycling 17 Nov
--   B (Wednesday) garbage 25 Nov, recycling 18 Nov
--   C (Thursday)  garbage 26 Nov, recycling 19 Nov
--   D (Friday)    garbage 27 Nov, recycling 20 Nov
--
-- generated_through is the day before this calendar was read, so the top-up
-- materialises from then on and never backfills the past.
-- --------------------------------------------------------------------------
insert into collection_schedule_rule
(municipality_id, sector, collection_type, bin_color, anchor_date, interval_weeks, valid_until,
 generated_through, note_fr, note_en, note_zh, source_url)
values
(current_setting('app.st')::bigint, 'a', 'garbage', 'black', '2026-11-24', 2, null, '2026-11-16',
 'Ordures menageres, le mardi, une semaine sur deux, en alternance avec le bac bleu.',
 'Household waste, every other Tuesday, alternating with the blue bin.',
 '生活垃圾，隔周周二收集，与蓝桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneA.pdf')),
(current_setting('app.st')::bigint, 'a', 'recycling', 'blue', '2026-11-17', 2, null, '2026-11-16',
 'Matieres recyclables, le mardi, une semaine sur deux, en alternance avec le bac noir.',
 'Recycling, every other Tuesday, alternating with the black bin.',
 '可回收物，隔周周二收集，与黑桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneA.pdf')),

(current_setting('app.st')::bigint, 'b', 'garbage', 'black', '2026-11-25', 2, null, '2026-11-16',
 'Ordures menageres, le mercredi, une semaine sur deux, en alternance avec le bac bleu.',
 'Household waste, every other Wednesday, alternating with the blue bin.',
 '生活垃圾，隔周周三收集，与蓝桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneB.pdf')),
(current_setting('app.st')::bigint, 'b', 'recycling', 'blue', '2026-11-18', 2, null, '2026-11-16',
 'Matieres recyclables, le mercredi, une semaine sur deux, en alternance avec le bac noir.',
 'Recycling, every other Wednesday, alternating with the black bin.',
 '可回收物，隔周周三收集，与黑桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneB.pdf')),

(current_setting('app.st')::bigint, 'c', 'garbage', 'black', '2026-11-26', 2, null, '2026-11-16',
 'Ordures menageres, le jeudi, une semaine sur deux, en alternance avec le bac bleu.',
 'Household waste, every other Thursday, alternating with the blue bin.',
 '生活垃圾，隔周周四收集，与蓝桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneC.pdf')),
(current_setting('app.st')::bigint, 'c', 'recycling', 'blue', '2026-11-19', 2, null, '2026-11-16',
 'Matieres recyclables, le jeudi, une semaine sur deux, en alternance avec le bac noir.',
 'Recycling, every other Thursday, alternating with the black bin.',
 '可回收物，隔周周四收集，与黑桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneC.pdf')),

(current_setting('app.st')::bigint, 'd', 'garbage', 'black', '2026-11-27', 2, null, '2026-11-16',
 'Ordures menageres, le vendredi, une semaine sur deux, en alternance avec le bac bleu.',
 'Household waste, every other Friday, alternating with the blue bin.',
 '生活垃圾，隔周周五收集，与蓝桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneD.pdf')),
(current_setting('app.st')::bigint, 'd', 'recycling', 'blue', '2026-11-20', 2, null, '2026-11-16',
 'Matieres recyclables, le vendredi, une semaine sur deux, en alternance avec le bac noir.',
 'Recycling, every other Friday, alternating with the black bin.',
 '可回收物，隔周周五收集，与黑桶交替。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneD.pdf'));

-- --------------------------------------------------------------------------
-- 4. Organics: every zone, Mondays, weekly or fortnightly by season.
--
-- Three consecutive rules, each ending where the next begins:
--   weekly       to 30 Nov 2026  (grid: 2, 9, 16, 23, 30 November)
--   fortnightly  14 Dec 2026 to 31 Mar 2027  (grid: 14 and 28 December)
--   weekly       5 Apr 2027 to 29 Nov 2027   (the first and last Mondays of
--                April to November 2027, from the page's standing rule)
-- --------------------------------------------------------------------------
insert into collection_schedule_rule
(municipality_id, sector, collection_type, bin_color, anchor_date, interval_weeks, valid_until,
 generated_through, note_fr, note_en, note_zh, source_url)
values
(current_setting('app.st')::bigint, 'all', 'organic', 'brown', '2026-11-02', 1, '2026-11-30', '2026-11-16',
 'Matieres organiques, tous les lundis d''avril a novembre, pour toutes les zones.',
 'Organics, every Monday from April to November, in every zone.',
 '有机垃圾，4 月至 11 月每周一收集，所有区相同。',
 current_setting('app.page')),
(current_setting('app.st')::bigint, 'all', 'organic', 'brown', '2026-12-14', 2, '2027-03-31', '2026-11-16',
 'Matieres organiques, un lundi sur deux de decembre a mars, pour toutes les zones.',
 'Organics, every other Monday from December to March, in every zone.',
 '有机垃圾，12 月至 3 月隔周周一收集，所有区相同。',
 current_setting('app.page')),
(current_setting('app.st')::bigint, 'all', 'organic', 'brown', '2027-04-05', 1, '2027-11-29', '2026-11-16',
 'Matieres organiques, tous les lundis d''avril a novembre, pour toutes les zones.',
 'Organics, every Monday from April to November, in every zone.',
 '有机垃圾，4 月至 11 月每周一收集，所有区相同。',
 current_setting('app.page'));

-- --------------------------------------------------------------------------
-- 5. The two Friday holidays zone D's grid moves a day earlier.
-- --------------------------------------------------------------------------
insert into collection_holiday
(municipality_id, holiday_date, name_fr, name_en, name_zh, shift_days, sector, source_url) values
(current_setting('app.st')::bigint, '2026-12-25', 'Noel', 'Christmas Day', '圣诞节', -1, 'all',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneD.pdf')),
(current_setting('app.st')::bigint, '2027-01-01', 'Jour de l''An', 'New Year''s Day', '元旦', -1, 'all',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_ZoneD.pdf'));

-- --------------------------------------------------------------------------
-- 6. The one bulky-item pickup left on the 2026 calendar.
--
-- Encombrants fall on the first Monday of most months, for every zone, but
-- the page states no rule for them and "first Monday" is not a weekly
-- pattern, so only the printed date still ahead is loaded, as a row of its
-- own rather than a rule. auto_generated stays false: no rule produced it,
-- and a calendar rebuild must not delete it.
-- --------------------------------------------------------------------------
insert into collection_event
(municipality_id, collection_date, sector, collection_type, bin_color, note_fr, note_en, note_zh, source_url)
values
(current_setting('app.st')::bigint, '2026-12-07', 'all', 'bulky', 'none',
 'Collecte des encombrants, pour toutes les zones.',
 'Bulky item collection, in every zone.',
 '大件垃圾收集，所有区相同。',
 concat(current_setting('app.pdf'), 'VST_Calendrier-collectes-2026_4Zones.pdf'));

-- --------------------------------------------------------------------------
-- 7. Last, and only for what was loaded.
-- --------------------------------------------------------------------------
update municipality set calendar_verified = true where id = current_setting('app.st')::bigint;
