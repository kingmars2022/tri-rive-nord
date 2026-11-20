# -*- coding: utf-8 -*-
"""Emits V33: Rosemere's sorting guide, read from its 2026 municipal calendar.

Source: the Town of Rosemere's "Calendrier 2026", archived as
data/municipal-sources/rosemere/2026-11-20/manual_calendrier-municipal-2026.pdf
(the same document V32 reads):

  p.30  "Collecte des matieres residuelles - Liste des matieres acceptees et
        refusees", French and English side by side: what the brown, blue and
        grey bins take and refuse, and that blue-bin items go in loose, not in
        plastic bags.
  p.31  the collection is "matieres organiques/residus verts" - green waste
        goes with organics - weekly May to November, every other week in
        winter.
  p.7-29  the month grids, for the Mondays with large-item, branch and
        Christmas-tree pickups in 2026.

Every card cites the town's site: the PDF's own address was not recorded when
it was downloaded, and the site is where a resident checks.

What is NOT here, by the rule docs/loading-a-city.md and V28 follow: where the
calendar only says an item is refused by a bin and never where it goes - plastic
wrap, styrofoam, parchment paper, windows and drinking glasses, propane tanks
and aerosols, large metal items and small appliances, construction materials,
household hazardous waste, computer equipment - there is no card. The
ecocentre is listed with its address and hours (p.5) but not with what it
takes, so there are no ecocentre cards either.

    python3 docs/verification/generate_v33_rosemere_guide.py
"""
import io
import os

import pg_sql

SITE = 'https://www.ville.rosemere.qc.ca/'

BIN = {'organic': 'brown', 'recycling': 'blue', 'garbage': 'grey',
       'ecocentre': 'none', 'bulky': 'none', 'special': 'none'}

LOOSE = (" Deposez-le en vrac dans le bac, pas dans un sac de plastique.",
         " Put it in the bin loose, not in a plastic bag.",
         "请散放入桶内，不要装在塑料袋里。")


def item(key, dest, names, instr, examples, keywords, availability=None):
    return dict(key=key, dest=dest, names=names, instr=instr, examples=examples,
                keywords=keywords, availability=availability)


def brown(key, names, what, examples, keywords):
    fr, en, zh = what
    return item(key, 'organic', names,
                (f"{fr} vont au bac brun.", f"{en} go in the brown bin.", f"{zh}放入棕桶。"),
                examples, keywords)


def blue(key, names, instr, examples, keywords):
    return item(key, 'recycling', names, tuple(a + b for a, b in zip(instr, LOOSE)), examples, keywords)


def grey(key, names, what, examples, keywords):
    fr, en, zh = what
    return item(key, 'garbage', names,
                (f"{fr} vont au bac gris.", f"{en} go in the grey bin.", f"{zh}放入灰桶。"),
                examples, keywords)


ITEMS = [
    # ------------------------------------------------------------ bac brun (p.30, "Residus alimentaires")
    brown('fruits-and-vegetables',
          ("Fruits et legumes", "Fruits and vegetables", "水果和蔬菜"),
          ("Les fruits et legumes, crus ou cuits, pelures comprises,",
           "Fruits and vegetables, raw or cooked, peels included,", "生熟水果和蔬菜（包括果皮菜皮）"),
          (["pelures", "restes de legumes"], ["peels", "vegetable scraps"], ["果皮", "菜叶"]),
          (["fruits", "legumes", "pelures", "epluchures"], ["fruits", "vegetables", "peels", "vegetable"],
           ["水果", "蔬菜", "果皮"])),
    brown('meat-and-bones',
          ("Viandes et os", "Meat and bones", "肉类和骨头"),
          ("Les viandes, crues ou cuites, et les os", "Meat, raw or cooked, and bones", "生熟肉类和骨头"),
          (["viande crue", "viande cuite", "os"], ["raw meat", "cooked meat", "bones"], ["生肉", "熟肉", "骨头"]),
          (["viande", "viandes", "ossements", "poulet"], ["meat", "bones", "chicken"], ["肉", "骨头", "鸡肉"])),
    brown('fish-and-seafood',
          ("Poissons et fruits de mer", "Fish and seafood", "鱼类和海鲜"),
          ("Les poissons et les fruits de mer, crus ou cuits,", "Fish and seafood, raw or cooked,",
           "生熟鱼类和海鲜"),
          (["poisson", "crevettes"], ["fish", "shrimp"], ["鱼", "虾"]),
          (["poisson", "poissons", "fruits de mer", "crevettes"], ["fish", "seafood", "shrimp"],
           ["鱼", "海鲜", "虾"])),
    brown('eggs-and-shells',
          ("Oeufs et coquilles", "Eggs and shells", "鸡蛋和蛋壳"),
          ("Les oeufs et leurs coquilles", "Eggs and their shells", "鸡蛋和蛋壳"),
          (["coquilles d'oeufs"], ["eggshells"], ["蛋壳"]),
          (["oeufs", "oeuf", "coquilles"], ["eggs", "egg", "eggshells", "shells"], ["鸡蛋", "蛋壳"])),
    brown('pasta-bread-and-grains',
          ("Pates, pain et cereales", "Pasta, bread and grains", "面食、面包和谷物"),
          ("Les pates alimentaires, le pain et les produits cerealiers", "Pasta, bread and grain products",
           "面食、面包和谷物制品"),
          (["pain", "pates", "riz"], ["bread", "pasta", "rice"], ["面包", "面条", "米饭"]),
          (["pates", "pain", "cereales", "riz"], ["pasta", "bread", "grains", "cereal", "rice"],
           ["面包", "面条", "谷物", "米饭"])),
    brown('flour-and-sugar',
          ("Farine, sucre et aliments en poudre", "Flour, sugar and dried goods", "面粉、糖和粉状食品"),
          ("La farine, le sucre et les autres produits alimentaires en poudre", "Flour, sugar and other dried goods",
           "面粉、糖和其他粉状食品"),
          (["farine", "sucre"], ["flour", "sugar"], ["面粉", "糖"]),
          (["farine", "sucre", "poudre"], ["flour", "sugar", "dried goods"], ["面粉", "糖"])),
    brown('solid-dairy',
          ("Produits laitiers solides", "Solid dairy products", "固体乳制品"),
          ("Les produits laitiers solides", "Solid dairy products", "固体乳制品"),
          (["fromage", "beurre"], ["cheese", "butter"], ["奶酪", "黄油"]),
          (["fromage", "beurre", "laitiers", "yogourt"], ["dairy", "cheese", "butter", "yogurt"],
           ["奶酪", "黄油", "乳制品"])),
    brown('coffee-and-tea',
          ("Cafe, the et tisane", "Coffee and tea", "咖啡和茶"),
          ("Les residus de the, de tisane ou de cafe, avec les sachets et les filtres de papier",
           "Coffee grounds, tea leaves and bags, and paper filters", "咖啡渣、茶叶、茶包和纸质过滤器"),
          (["marc de cafe", "sachets de the", "filtres a cafe"], ["coffee grounds", "tea bags", "coffee filters"],
           ["咖啡渣", "茶包", "咖啡滤纸"]),
          (["cafe", "the", "tisane", "filtre"], ["coffee", "tea", "grounds", "filter"], ["咖啡", "茶", "滤纸"])),
    # ------------------------------------------------------------ bac brun (p.30, "Autres matieres compostables")
    brown('compostable-dishes',
          ("Vaisselle compostable", "Compostable dishes", "可堆肥餐具"),
          ("La vaisselle jetable compostable", "Compostable disposable dishes", "可堆肥一次性餐具"),
          (["assiettes compostables"], ["compostable plates"], ["可堆肥盘子"]),
          (["vaisselle compostable", "compostable"], ["compostable", "compostable dishes"], ["可堆肥餐具"])),
    brown('food-soiled-paper',
          ("Papier et carton souilles", "Food-soiled paper and cardboard", "被食物弄脏的纸和纸板"),
          ("Les papiers et cartons souilles de residus alimentaires et non recyclables",
           "Non-recyclable paper and cardboard soiled with food", "沾了食物残渣、不能回收的纸和纸板"),
          (["boite de pizza", "assiettes de carton"], ["pizza box", "paper plates"], ["披萨盒", "纸盘"]),
          (["pizza", "souille", "souilles", "assiettes"],
           ["pizza", "soiled", "paper plates", "greasy"], ["披萨盒", "纸盘", "脏纸板"])),
    brown('sanitary-fibres',
          ("Essuie-tout et mouchoirs", "Paper towels and tissues", "厨房纸和纸巾"),
          ("Les fibres sanitaires (papier essuie-mains, mouchoirs, etc.), sauf les couches et les serviettes "
           "hygieniques,",
           "Sanitary fibres (paper towels, facial tissues, etc.), but not diapers or sanitary napkins,",
           "卫生纸类（擦手纸、纸巾等，不包括尿布和卫生巾）"),
          (["papier essuie-mains", "mouchoirs"], ["paper towels", "facial tissues"], ["擦手纸", "纸巾"]),
          (["essuie-tout", "mouchoirs", "essuie-mains"], ["paper towel", "tissues", "napkins"], ["厨房纸", "纸巾"])),
    brown('cellulose-paper-bags',
          ("Sacs de papier a pellicule de cellulose", "Paper bags with cellulose coating", "带纤维素膜的纸袋"),
          ("Les sacs de papier avec pellicule de cellulose", "Paper bags with cellulose coating",
           "带纤维素膜的纸袋"),
          (["sac de papier"], ["paper bag"], ["纸袋"]),
          (["sac de papier", "cellulose"], ["paper bag", "cellulose"], ["纸袋"])),
    brown('cold-ashes',
          ("Cendres froides", "Cold ashes", "冷却的灰烬"),
          ("Les cendres froides du foyer", "Cold fireplace ashes", "壁炉中已冷却的灰烬"),
          (["cendres de foyer"], ["fireplace ashes"], ["壁炉灰"]),
          (["cendres", "foyer"], ["ashes", "fireplace"], ["灰烬", "壁炉灰"])),
    item('green-waste', 'organic',
         ("Residus verts", "Green waste", "庭院废料"),
         ("Les residus verts sont ramasses avec les matieres organiques, au bac brun: chaque semaine de mai a "
          "novembre, une semaine sur deux l'hiver. Les branches ont leur propre collecte.",
          "Green waste is picked up with organics, in the brown bin: weekly from May to November, every other "
          "week in winter. Branches have their own pickup.",
          "庭院废料随有机垃圾一起放入棕桶：5 月至 11 月每周收，冬季隔周收。树枝另有专门收集。"),
         (["residus de jardin"], ["garden waste"], ["花园废料"]),
         (["residus verts", "jardin"], ["green waste", "garden", "yard waste"], ["庭院废料", "花园"])),
    # ------------------------------------------------------------ bac bleu (p.30)
    blue('paper',
         ("Papier", "Paper", "纸张"),
         ("Le papier (journaux, revues, papier d'imprimante) va au bac bleu, sauf s'il est souille, cire ou "
          "plastifie.",
          "Paper (newspaper, magazines, printer paper) goes in the blue bin, unless soiled, waxed or laminated.",
          "纸张（报纸、杂志、打印纸）放入蓝桶；脏污、上蜡或覆膜的纸除外。"),
         (["journaux", "revues"], ["newspaper", "magazines"], ["报纸", "杂志"]),
         (["papier", "journal", "journaux", "revues"], ["paper", "newspaper", "magazines"], ["纸", "报纸", "杂志"])),
    blue('glass',
         ("Verre", "Glass", "玻璃"),
         ("Le verre va au bac bleu. Pas les vitres, les fenetres, les verres a boire, la vaisselle ni les miroirs.",
          "Glass goes in the blue bin. Not glass panes, windows, drinking glasses, dishes or mirrors.",
          "玻璃放入蓝桶。窗玻璃、窗户、玻璃杯、餐具和镜子除外。"),
         (["bouteilles de verre", "pots de verre"], ["glass bottles", "glass jars"], ["玻璃瓶", "玻璃罐"]),
         (["verre", "bouteille", "pot"], ["glass", "bottle", "jar"], ["玻璃", "瓶子"])),
    blue('plastic-containers',
         ("Plastiques", "Plastic", "塑料"),
         ("Tous les contenants de plastique, ainsi que les plastiques nos 1, 2, 3, 4, 5 et 7, vont au bac bleu. "
          "Pas le plastique no 6.",
          "All plastic containers, as well as plastics #1, 2, 3, 4, 5 and 7, go in the blue bin. Not #6 plastic.",
          "所有塑料容器，以及 1、2、3、4、5、7 号塑料，放入蓝桶。6 号塑料除外。"),
         (["contenants de plastique"], ["plastic containers"], ["塑料容器"]),
         (["plastique", "contenant", "bouteille de plastique"], ["plastic", "container", "plastic bottle"],
          ["塑料", "塑料瓶", "塑料容器"])),
    blue('metal-cans',
         ("Metal", "Metal", "金属"),
         ("Le metal (boites de conserve, etc.) va au bac bleu. Pas les gros objets en metal.",
          "Metal (food cans, etc.) goes in the blue bin. Not large metal items.",
          "金属（罐头盒等）放入蓝桶。大型金属物品除外。"),
         (["boites de conserve"], ["food cans"], ["罐头盒"]),
         (["metal", "conserve", "canette"], ["metal", "can", "cans", "tin"], ["金属", "罐头"])),
    blue('cardboard',
         ("Carton", "Cardboard", "纸板"),
         ("Le carton va au bac bleu. Souille de nourriture, il va au bac brun.",
          "Cardboard goes in the blue bin. Soiled with food, it goes in the brown bin.",
          "纸板放入蓝桶。沾了食物的纸板放入棕桶。"),
         (["boites de carton"], ["cardboard boxes"], ["纸箱"]),
         (["carton", "boite"], ["cardboard", "box"], ["纸板", "纸箱"])),
    # ------------------------------------------------------------ bac gris (p.30)
    grey('lightbulbs',
         ("Ampoules electriques", "Lightbulbs", "灯泡"),
         ("Les ampoules electriques", "Lightbulbs", "灯泡"),
         (["ampoule"], ["light bulb"], ["灯泡"]),
         (["ampoule", "ampoules"], ["lightbulb", "light bulb", "bulb"], ["灯泡"])),
    grey('garden-hoses',
         ("Boyaux d'arrosage", "Water hoses", "浇水软管"),
         ("Les boyaux d'arrosage", "Water hoses", "浇水软管"),
         (["boyau"], ["garden hose"], ["软管"]),
         (["boyau", "arrosage"], ["hose", "garden hose"], ["软管", "水管"])),
    grey('dryer-lint',
         ("Charpie de secheuse", "Dryer lint", "干衣机绒毛"),
         ("La charpie de secheuse", "Dryer lint", "干衣机绒毛"),
         (["charpie"], ["lint"], ["绒毛"]),
         (["charpie", "secheuse"], ["lint", "dryer"], ["绒毛", "干衣机"])),
    grey('diapers',
         ("Couches et serviettes hygieniques", "Diapers and sanitary napkins", "尿布和卫生巾"),
         ("Les couches et les serviettes hygieniques", "Diapers and sanitary napkins", "尿布和卫生巾"),
         (["couches"], ["diapers"], ["尿布"]),
         (["couches", "couche", "serviettes hygieniques"], ["diapers", "diaper", "sanitary napkins"],
          ["尿布", "卫生巾"])),
    grey('waxed-wrapping',
         ("Emballages cires", "Waxed wrapping paper", "蜡纸包装"),
         ("Les emballages cires pour la viande ou le fromage", "Waxed paper used to wrap meat or cheese",
          "包肉或奶酪用的蜡纸包装"),
         (["papier de boucherie"], ["butcher paper"], ["肉铺包装纸"]),
         (["emballage cire", "cire"], ["waxed paper", "wax", "wrapping"], ["蜡纸"])),
    grey('soiled-litter',
         ("Litiere souillee", "Soiled litter", "用过的猫砂"),
         ("La litiere souillee et les excrements d'animaux", "Soiled litter and pet excrement",
          "用过的猫砂和动物粪便"),
         (["litiere de chat"], ["cat litter"], ["猫砂"]),
         (["litiere", "excrements", "chat"], ["litter", "cat litter", "excrement"], ["猫砂", "粪便"])),
    grey('broken-mirrors-and-dishes',
         ("Miroirs et vaisselle brises", "Broken mirrors and dishes", "破碎的镜子和餐具"),
         ("Les miroirs et la vaisselle brises", "Broken mirrors and dishes", "破碎的镜子和餐具"),
         (["assiette cassee"], ["broken plate"], ["破盘子"]),
         (["miroir", "vaisselle", "brises", "verre casse"], ["mirror", "dishes", "broken", "broken glass"],
          ["镜子", "餐具", "碎玻璃"])),
    grey('waxed-paper-containers',
         ("Papier cire", "Wax-lined containers", "蜡纸容器"),
         ("Le papier cire, comme les contenants de creme glacee,", "Wax-lined food containers, like ice cream "
          "containers,", "蜡纸及蜡纸食品容器（如冰淇淋盒）"),
         (["contenant de creme glacee"], ["ice cream container"], ["冰淇淋盒"]),
         (["papier cire", "creme glacee"], ["wax-lined", "ice cream container"], ["冰淇淋盒", "蜡纸"])),
    grey('pottery',
         ("Poterie", "Pottery", "陶器"),
         ("La poterie", "Pottery", "陶器"),
         (["pot de terre cuite"], ["clay pot"], ["陶盆"]),
         (["poterie", "terre cuite"], ["pottery", "clay pot", "ceramic"], ["陶器", "陶盆"])),
    grey('dental-floss',
         ("Soie dentaire", "Dental floss", "牙线"),
         ("La soie dentaire", "Dental floss", "牙线"),
         (["fil dentaire"], ["floss"], ["牙线"]),
         (["soie dentaire", "dentaire"], ["dental floss", "floss"], ["牙线"])),
    # ------------------------------------------------------------ pickups (month grids, p.7-29)
    item('large-items', 'bulky',
         ("Objets encombrants", "Large items", "大件物品"),
         ("La collecte des objets encombrants a lieu un lundi par mois, dans tous les secteurs.",
          "Large items are picked up one Monday a month, in every sector.",
          "大件物品每月收集一次（周一），所有区相同。"),
         (["meubles", "matelas"], ["furniture", "mattress"], ["家具", "床垫"]),
         (["encombrants", "meubles", "matelas", "divan"], ["large items", "furniture", "mattress", "couch"],
          ["大件", "家具", "床垫", "沙发"]),
         ("En 2026: 12 janvier, 9 fevrier, 9 mars, 13 avril, 11 mai, 8 juin, 13 juillet, 10 aout, "
          "14 septembre, 12 octobre, 9 novembre et 14 decembre.",
          "In 2026: 12 January, 9 February, 9 March, 13 April, 11 May, 8 June, 13 July, 10 August, "
          "14 September, 12 October, 9 November and 14 December.",
          "2026 年：1 月 12 日、2 月 9 日、3 月 9 日、4 月 13 日、5 月 11 日、6 月 8 日、7 月 13 日、"
          "8 月 10 日、9 月 14 日、10 月 12 日、11 月 9 日、12 月 14 日。")),
    item('branches', 'special',
         ("Branches", "Branches", "树枝"),
         ("Les branches ont leur propre collecte, un lundi par mois d'avril a novembre.",
          "Branches have their own pickup, one Monday a month from April to November.",
          "树枝有专门的收集，4 月至 11 月每月一次（周一）。"),
         (["branches d'arbres"], ["tree branches"], ["树枝"]),
         (["branches", "branche", "elagage"], ["branches", "branch", "pruning"], ["树枝", "修剪"]),
         ("En 2026: 20 avril, 18 mai, 15 juin, 20 juillet, 17 aout, 21 septembre, 19 octobre et 16 novembre.",
          "In 2026: 20 April, 18 May, 15 June, 20 July, 17 August, 21 September, 19 October and 16 November.",
          "2026 年：4 月 20 日、5 月 18 日、6 月 15 日、7 月 20 日、8 月 17 日、9 月 21 日、10 月 19 日、"
          "11 月 16 日。")),
    item('christmas-trees', 'special',
         ("Sapins de Noel", "Christmas trees", "圣诞树"),
         ("Les sapins sont ramasses lors d'une collecte en janvier.",
          "Christmas trees are picked up in one January collection.",
          "圣诞树在一月的一次专门收集中收走。"),
         (["sapin naturel"], ["real Christmas tree"], ["真圣诞树"]),
         (["sapin", "noel"], ["christmas tree", "christmas"], ["圣诞树"]),
         ("En 2026: le lundi 12 janvier. La date de 2027 n'est pas encore publiee.",
          "In 2026: Monday 12 January. The 2027 date is not published yet.",
          "2026 年是 1 月 12 日（周一）。2027 年的日期尚未公布。")),
]


def q(s):
    return "'" + s.replace("'", "''") + "'" if s is not None else 'null'


def render():
    keys = [i['key'] for i in ITEMS]
    assert len(keys) == len(set(keys)), 'item_key must be unique within the city'
    for i in ITEMS:
        for lang_words in i['keywords']:
            for word in lang_words:
                if word.isascii():
                    assert len(word) >= 3, (i['key'], word)
        for text in i['names'] + i['instr']:
            assert not any(c in text for c in 'àâäéèêëîïôöùûüçœÀÉÈ'), (i['key'], text[:40])
    out = [HEADER.format(n=len(ITEMS))]
    for i in ITEMS:
        out.append("insert into sorting_item (municipality_id, item_key, destination_type, bin_color, source_url) "
                   f"values (@rm, {q(i['key'])}, {q(i['dest'])}, {q(BIN[i['dest']])}, {q(SITE)});")
        out.append("set @id = last_insert_id();")
        av = i['availability'] or (None, None, None)
        rows = ", ".join(f"(@id, {q(lang)}, {q(n)}, {q(ins)}, null, {q(a)})"
                         for lang, n, ins, a in zip(('fr', 'en', 'zh'), i['names'], i['instr'], av))
        out.append("insert into sorting_item_translation (item_id, language_code, name, instruction, location, "
                   f"availability) values {rows};")
        ex = []
        for lang, values in zip(('fr', 'en', 'zh'), i['examples']):
            ex += [f"(@id, {q(lang)}, {pos}, {q(v)})" for pos, v in enumerate(values)]
        out.append("insert into sorting_item_example (item_id, language_code, position, example) values "
                   + ", ".join(ex) + ";")
        kw = []
        for lang, values in zip(('fr', 'en', 'zh'), i['keywords']):
            kw += [f"(@id, {q(lang)}, {q(v)})" for v in values]
        out.append("insert into sorting_item_keyword (item_id, language_code, keyword) values "
                   + ", ".join(kw) + ";")
        out.append("")
    out.append("-- Last, and only for what was loaded.\n"
               "update municipality set guide_verified = true where id = @rm;\n")
    return "\n".join(out)


HEADER = """-- --------------------------------------------------------------------------
-- Rosemere's sorting guide.
--
-- GENERATED by docs/verification/generate_v33_rosemere_guide.py; edit that,
-- not this. {n} items, three languages.
--
-- Read from the town's 2026 municipal calendar (the document V32 reads):
-- page 30's lists of what the brown, blue and grey bins accept and refuse,
-- page 31's "organics/green waste" collection, and the month grids for the
-- 2026 large-item, branch and Christmas-tree Mondays. Cards cite the town's
-- site; the PDF's own address was not recorded.
--
-- What residents might not guess, and this guide says: lightbulbs, garden
-- hoses, dryer lint, pottery and soiled litter go in the GREY bin; cold
-- fireplace ashes and paper towels go in the BROWN bin; blue-bin items go in
-- loose, never in a plastic bag; #6 plastic is not recycled.
--
-- Not loaded: what the calendar only refuses and never places (plastic wrap,
-- styrofoam, parchment paper, windows and drinking glasses, propane tanks,
-- aerosols, large metal items, small appliances, construction materials,
-- household hazardous waste, computer equipment), and ecocentre cards, since
-- the calendar gives the ecocentre's address and hours but not what it takes.
-- --------------------------------------------------------------------------

set @rm = (select id from municipality where slug = 'rosemere');
"""

if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    target = os.path.join(root, 'backend/src/main/resources/db/migration/V33__rosemere_sorting_guide.sql')
    io.open(target, 'w', encoding='utf-8', newline='\n').write(pg_sql.convert(render()))
    print(f'wrote {target}: {len(ITEMS)} items')
