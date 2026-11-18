-- --------------------------------------------------------------------------
-- Sainte-Therese: search words for the aerosol entry. Not a change to any rule.
--
-- The same cross-city effect V24 fixed for Blainville. Full-text relevance is
-- weighted by word statistics over every city's rows, and V28 added
-- Boisbriand's guide. After it, a search for Sainte-Therese's own "Aerosol
-- cans" returned its "Tin cans" entry, which carries the keyword "cans"
-- while the aerosol entry had no keyword with that word. Caught by
-- SainteThereseGuideIntegrationTest.everyEntryIsTheTopAnswerForItsOwnName,
-- run after loading Boisbriand, as docs/loading-a-city.md says to.
--
-- The entry gets the words of its own name, in the language that lost it.
-- --------------------------------------------------------------------------

set @st = (select id from municipality where slug = 'sainte-therese');
set @item = (select id from sorting_item where municipality_id = @st and item_key = 'aerosol-cans');

insert into sorting_item_keyword (item_id, language_code, keyword) values
(@item, 'en', 'aerosol cans'),
(@item, 'en', 'aerosols');
