-- --------------------------------------------------------------------------
-- What simulated residents asked and did not get. Search words and one card;
-- no rule changes.
--
-- A battery of 88 questions worded the way residents type (see
-- docs/verification/resident-simulation.md) found two kinds of miss that
-- data fixes:
--
--   * Everyday words a guide does not use: "pelures de patate", "divan" and
--     "kleenex" in Blainville, "laptop" in Sainte-Therese, "tree stump" in
--     Boisbriand, "leaves and grass" in Rosemere. They are added as keywords
--     to the entry the guide already sends them to. (Plurals such as
--     Boisbriand's "chaudrons" are fixed in the search itself.)
--   * Rosemere's calendar refuses a list of things in its bins without
--     saying where they go. With no card for them, "old paint cans" found
--     the blue-bin metal card on the word "cans". A card now says what the
--     calendar does: refused in the bins, ask the town, with the number and
--     address the calendar prints (pages 5 and 31).
-- --------------------------------------------------------------------------

insert into sorting_item_keyword (item_id, language_code, keyword)
select i.id, k.language_code, k.keyword
from sorting_item i
join municipality m on m.id = i.municipality_id
join (values
    ('blainville', 'fruit-and-vegetable-scraps', 'fr', 'pelure'),
    ('blainville', 'fruit-and-vegetable-scraps', 'fr', 'epluchure'),
    ('blainville', 'fruit-and-vegetable-scraps', 'fr', 'patate'),
    ('blainville', 'fruit-and-vegetable-scraps', 'en', 'peel'),
    ('blainville', 'fruit-and-vegetable-scraps', 'en', 'potato'),
    ('blainville', 'fruit-and-vegetable-scraps', 'zh', '果皮'),
    ('blainville', 'bulky-items', 'fr', 'divan'),
    ('blainville', 'bulky-items', 'fr', 'sofa'),
    ('blainville', 'bulky-items', 'en', 'couch'),
    ('blainville', 'bulky-items', 'en', 'sofa'),
    ('blainville', 'bulky-items', 'zh', '沙发'),
    ('blainville', 'tissues-and-paper-napkins', 'fr', 'kleenex'),
    ('blainville', 'tissues-and-paper-napkins', 'en', 'kleenex'),
    ('sainte-therese', 'electronics', 'fr', 'portable'),
    ('sainte-therese', 'electronics', 'en', 'laptop'),
    ('sainte-therese', 'electronics', 'zh', '笔记本电脑'),
    ('boisbriand', 'stumps-and-logs', 'fr', 'souche d''arbre'),
    ('boisbriand', 'stumps-and-logs', 'en', 'tree stump'),
    ('rosemere', 'green-waste', 'fr', 'feuilles'),
    ('rosemere', 'green-waste', 'fr', 'gazon'),
    ('rosemere', 'green-waste', 'en', 'leaves'),
    ('rosemere', 'green-waste', 'en', 'grass'),
    ('rosemere', 'green-waste', 'zh', '树叶'),
    ('rosemere', 'green-waste', 'zh', '草屑'),
    ('rosemere', 'metal-cans', 'en', 'food can'),
    ('rosemere', 'metal-cans', 'en', 'tin can'),
    -- With plurals folded, "paper towels" in the tissues entry's name also
    -- fits this entry's own name; its distinguishing words become a phrase.
    ('sainte-therese', 'paper-towel-with-chemicals-on-it', 'en', 'cleaning products')
) as k(slug, item_key, language_code, keyword)
  on k.slug = m.slug and k.item_key = i.item_key;

-- Rosemere's metal card had "can" and "cans" on their own, which also fit
-- "paint cans"; the phrases above say which cans the blue bin takes.
delete from sorting_item_keyword
where language_code = 'en' and keyword in ('can', 'cans')
  and item_id = (select i.id from sorting_item i join municipality m on m.id = i.municipality_id
                 where m.slug = 'rosemere' and i.item_key = 'metal-cans');

select set_config('app.rm', ((select id from municipality where slug = 'rosemere'))::text, false);

insert into sorting_item (municipality_id, item_key, destination_type, bin_color, source_url)
values (current_setting('app.rm')::bigint, 'refused-in-every-bin', 'special', 'none', 'https://www.ville.rosemere.qc.ca/');
select set_config('app.id', lastval()::text, false);

insert into sorting_item_translation (item_id, language_code, name, instruction, location, availability) values
(current_setting('app.id')::bigint, 'fr', 'Refuses dans les bacs',
 'Le calendrier de la Ville refuse ces matieres dans les bacs sans dire ou les apporter: residus domestiques '
 || 'dangereux, materiaux de construction, equipement informatique, bonbonnes de propane, bouteilles en aerosol, '
 || 'gros objets en metal, petits electromenagers, styromousse et emballages de plastique. Renseignez-vous aupres '
 || 'des Travaux publics: travauxpublics@ville.rosemere.qc.ca, 450 621-3500, poste 3300.',
 'Ecocentre de Rosemere, 190, rue Charbonneau (poste 3317).', null),
(current_setting('app.id')::bigint, 'en', 'Refused in the bins',
 'The town''s calendar refuses these in its bins without saying where they go: household hazardous waste, '
 || 'construction materials, computer equipment, propane tanks, aerosol cans, large metal items, small appliances, '
 || 'styrofoam and plastic wrap. Ask Public Works: travauxpublics@ville.rosemere.qc.ca, 450 621-3500, ext. 3300.',
 'Rosemere ecocentre, 190 rue Charbonneau (ext. 3317).', null),
(current_setting('app.id')::bigint, 'zh', '不能放进任何桶的物品',
 '市政府日历规定以下物品不能放进任何桶，但没有说明应送往何处：家庭有害垃圾、建筑材料、电脑设备、丙烷罐、'
 || '喷雾罐、大型金属物品、小家电、泡沫塑料和塑料包装膜。请咨询市政工程部门：travauxpublics@ville.rosemere.qc.ca，'
 || '450 621-3500 转 3300。',
 'Rosemere 生态中心，190 rue Charbonneau（转 3317）。', null);

insert into sorting_item_example (item_id, language_code, position, example) values
(current_setting('app.id')::bigint, 'fr', 0, 'peinture'), (current_setting('app.id')::bigint, 'fr', 1, 'bonbonne de propane'),
(current_setting('app.id')::bigint, 'fr', 2, 'ordinateur'),
(current_setting('app.id')::bigint, 'en', 0, 'paint'), (current_setting('app.id')::bigint, 'en', 1, 'propane tank'),
(current_setting('app.id')::bigint, 'en', 2, 'computer'),
(current_setting('app.id')::bigint, 'zh', 0, '油漆'), (current_setting('app.id')::bigint, 'zh', 1, '丙烷罐'),
(current_setting('app.id')::bigint, 'zh', 2, '电脑');

insert into sorting_item_keyword (item_id, language_code, keyword) values
(current_setting('app.id')::bigint, 'fr', 'dangereux'),
(current_setting('app.id')::bigint, 'fr', 'peinture'),
(current_setting('app.id')::bigint, 'fr', 'solvant'),
(current_setting('app.id')::bigint, 'fr', 'construction'),
(current_setting('app.id')::bigint, 'fr', 'ordinateur'),
(current_setting('app.id')::bigint, 'fr', 'informatique'),
(current_setting('app.id')::bigint, 'fr', 'propane'),
(current_setting('app.id')::bigint, 'fr', 'aerosol'),
(current_setting('app.id')::bigint, 'fr', 'electromenager'),
(current_setting('app.id')::bigint, 'fr', 'styromousse'),
(current_setting('app.id')::bigint, 'en', 'hazardous'),
(current_setting('app.id')::bigint, 'en', 'paint'),
(current_setting('app.id')::bigint, 'en', 'solvent'),
(current_setting('app.id')::bigint, 'en', 'construction'),
(current_setting('app.id')::bigint, 'en', 'computer'),
(current_setting('app.id')::bigint, 'en', 'propane'),
(current_setting('app.id')::bigint, 'en', 'aerosol'),
(current_setting('app.id')::bigint, 'en', 'appliance'),
(current_setting('app.id')::bigint, 'en', 'styrofoam'),
(current_setting('app.id')::bigint, 'zh', '有害'),
(current_setting('app.id')::bigint, 'zh', '油漆'),
(current_setting('app.id')::bigint, 'zh', '建筑材料'),
(current_setting('app.id')::bigint, 'zh', '电脑'),
(current_setting('app.id')::bigint, 'zh', '丙烷'),
(current_setting('app.id')::bigint, 'zh', '喷雾罐'),
(current_setting('app.id')::bigint, 'zh', '小家电'),
(current_setting('app.id')::bigint, 'zh', '泡沫塑料');
