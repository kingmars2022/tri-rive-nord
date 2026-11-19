-- --------------------------------------------------------------------------
-- Blainville: search words for the food-soiled paper entry.
--
-- Not a change to any rule. Loading Sainte-Therese's guide (V23) made one of
-- Blainville's answers fragile, and this is why, because it will happen again
-- with every city:
--
-- MySQL computes full-text relevance from word statistics over the WHOLE
-- index. The search filters to one city, but the scores it filters are
-- weighted by every city's rows. V23 added 62 entries, many of them about
-- cardboard and soiled paper, so those words became commoner and weighed
-- less - in Blainville's results too. For "Where does soiled cardboard go?"
-- Blainville's correct entry led the next one 10.749 to 8.312 before V23 and
-- 6.097 to 6.090 after it; one integration run in two then returned
-- "Containers and packaging" instead.
--
-- The entry was winning on its name alone: its English keywords were "pizza"
-- and "paper towel", while the containers entry carries "cardboard". It now
-- has the words of its own name as keywords, in all three languages, which
-- is what keywords are for and which does not depend on what other cities
-- load.
--
-- A sweep that searches every Blainville entry by its own name in all three
-- languages, with and without Sainte-Therese's rows, is described in
-- docs/loading-a-city.md; run it after loading any city.
-- --------------------------------------------------------------------------

select set_config('app.blainville', ((select id from municipality where slug = 'blainville'))::text, false);
select set_config('app.item', ((select id from sorting_item
             where municipality_id = current_setting('app.blainville')::bigint and item_key = 'food-soiled-paper-and-cardboard'))::text, false);

insert into sorting_item_keyword (item_id, language_code, keyword) values
(current_setting('app.item')::bigint, 'en', 'soiled'),
(current_setting('app.item')::bigint, 'en', 'soiled cardboard'),
(current_setting('app.item')::bigint, 'fr', 'souille'),
(current_setting('app.item')::bigint, 'fr', 'carton souille'),
(current_setting('app.item')::bigint, 'zh', '弄脏的纸'),
(current_setting('app.item')::bigint, 'zh', '脏纸板');
