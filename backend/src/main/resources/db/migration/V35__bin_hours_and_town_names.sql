-- --------------------------------------------------------------------------
-- When bins may go out and must come back, per town; and the towns' names
-- spelled as they spell them.
--
-- Found by simulating residents: the home page told every town Blainville's
-- hours ("after 8 p.m. tonight and before 6 a.m.; bring it back before 8
-- p.m."), because the sentence was written when Blainville was the only town.
-- Each town prints its own:
--
--   Blainville      the hours the app has always shown;
--   Sainte-Therese  "Placez votre bac des 18 h la veille ou avant 7 h le matin
--                   meme de la collecte, et retirez-le au plus tard a 20 h la
--                   journee de la collecte" (collections page, archived under
--                   data/municipal-sources/sainte-therese/2026-11-17/);
--   Boisbriand      "Les bacs sont autorises en bordure de rue a partir de
--                   18 h la veille de la collecte et jusqu'a 22 h le jour de
--                   la collecte", collections "entre 7 h et 18 h" (2026
--                   calendar, page 1);
--   Rosemere        not printed in its calendar: left empty, and the page
--                   says nothing rather than another town's rule.
--
-- Times are local. bins_out_by is the morning limit (or the start of
-- collection, where that is what the town gives).
-- --------------------------------------------------------------------------

alter table municipality add column bins_out_from time;
alter table municipality add column bins_out_by time;
alter table municipality add column bins_in_by time;

update municipality set bins_out_from = '20:00', bins_out_by = '06:00', bins_in_by = '20:00'
where slug = 'blainville';
update municipality set bins_out_from = '18:00', bins_out_by = '07:00', bins_in_by = '20:00'
where slug = 'sainte-therese';
update municipality set bins_out_from = '18:00', bins_out_by = '07:00', bins_in_by = '22:00'
where slug = 'boisbriand';

-- V19 stored the names without accents. They are shown in the city switcher
-- and named in every assistant answer.
update municipality set name = 'Sainte-Thérèse' where slug = 'sainte-therese';
update municipality set name = 'Rosemère' where slug = 'rosemere';
