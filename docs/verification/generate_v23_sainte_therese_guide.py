# -*- coding: utf-8 -*-
"""Emits V23: Sainte-Therese's sorting guide, from the city's own regulation.

Sources, archived with their sha256 under
data/municipal-sources/sainte-therese/2026-11-17/:

  REG   Reglement 1373 N.S. relatif a la gestion des matieres residuelles,
        adopted 4 May 2026, in force 5 May 2026. Article 14 makes its annexes
        the binding lists: A (bac noir), B (bac bleu), C (bac brun),
        D (encombrants refused), E (ecocentre).
  PAGE  The city's "Collectes et ecocentre" page: what the bulky pickup takes,
        branches, green waste, Christmas trees, the brown-bin bag rule.
  ECO   The city's "Ecocentre" page: hours, address, what is free.

The city's own "Matieres acceptees" buttons for the blue and brown bins point
to RECYC-QUEBEC's "Ca va ou?" app, not to a list on its own site. The
regulation is used instead: it is the city's document, it is law, and it is
the more complete of the two.

Run from the repository root:
    python3 docs/verification/generate_v23_sainte_therese_guide.py
"""
import io

import pg_sql
import os

REG = ('https://www.sainte-therese.ca/storage/app/media/services/citoyens/'
       'collectes-et-ecocentre/1373_NS_Regl_mat_res.pdf')
PAGE = 'https://www.sainte-therese.ca/services/services-aux-citoyens/collectes-et-ecocentre'
ECO = 'https://www.sainte-therese.ca/services/services-aux-citoyens/ecocentre'

BIN = {'organic': 'brown', 'recycling': 'blue', 'garbage': 'black',
       'ecocentre': 'none', 'bulky': 'none', 'special': 'none'}

ECO_LOCATION = ('Ecocentre, 105 rue Blanchard, Sainte-Therese',
                'Ecocentre, 105 rue Blanchard, Sainte-Therese',
                '生态中心，105 rue Blanchard, Sainte-Therese')
ECO_HOURS = ('Vendredi 12 h a 19 h; samedi et dimanche 9 h a 18 h. Preuve de residence exigee. '
             'Ferme du 21 decembre 2026 au 7 janvier 2027.',
             'Friday noon to 7 p.m.; Saturday and Sunday 9 a.m. to 6 p.m. Proof of residence required. '
             'Closed from 21 December 2026 to 7 January 2027.',
             '周五 12:00-19:00；周六、周日 9:00-18:00。需出示居住证明。'
             '2026 年 12 月 21 日至 2027 年 1 月 7 日关闭。')

# Each item: key, destination, source, names fr/en/zh, instructions fr/en/zh,
# examples fr/en/zh, keywords fr/en/zh, and optionally location/availability.
#
# item_key reuses Blainville's key wherever the item is the same thing, even
# when - especially when - the answer differs; that is what makes "which city
# disagrees" a query. Keys new to this city are new things the regulation
# names that Blainville's guide does not.
#
# Keywords stay at three characters or more: MySQL's full-text index never
# holds a shorter token, which is how "os" once went unanswered (see V17).


def item(key, dest, src, names, instr, examples, keywords, location=None, availability=None):
    return dict(key=key, dest=dest, src=src, names=names, instr=instr,
                examples=examples, keywords=keywords, location=location,
                availability=availability)


ITEMS = [
    # ------------------------------------------------------------------
    # Bac brun - Annexe C
    # ------------------------------------------------------------------
    item('meat-and-bones', 'organic', REG,
         ("Viandes et os", "Meat and bones", "肉类和骨头"),
         ("Les viandes et les os vont au bac brun.",
          "Meat and bones go in the brown bin.",
          "肉类和骨头放入棕桶。"),
         (["viande", "os de poulet", "gras"], ["meat", "chicken bones", "fat"], ["肉", "骨头", "油脂"]),
         (["viande", "poulet", "ossements", "gras"], ["meat", "chicken", "bones", "fat"], ["肉", "鸡肉", "骨头"])),

    item('fish-and-seafood', 'organic', REG,
         ("Poissons et fruits de mer", "Fish and seafood", "鱼类和海鲜"),
         ("Les poissons, les fruits de mer et leurs coquilles vont au bac brun.",
          "Fish, seafood and their shells go in the brown bin.",
          "鱼、海鲜及其外壳放入棕桶。"),
         (["poisson", "crevettes", "coquilles de moules"], ["fish", "shrimp", "mussel shells"], ["鱼", "虾", "贝壳"]),
         (["poisson", "fruits de mer", "coquillages", "crevette"], ["fish", "seafood", "shellfish", "shrimp"],
          ["鱼", "海鲜", "贝壳", "虾"])),

    item('eggs-and-eggshells', 'organic', REG,
         ("Coquilles d'oeufs", "Eggshells", "蛋壳"),
         ("Les coquilles d'oeufs vont au bac brun.",
          "Eggshells go in the brown bin.",
          "蛋壳放入棕桶。"),
         (["coquilles d'oeufs"], ["eggshells"], ["蛋壳"]),
         # Plurals as their own keywords: "oeufs" never matches "oeuf".
         (["oeuf", "oeufs", "coquille", "coquilles"], ["egg", "eggs", "eggshell", "eggshells"], ["鸡蛋", "蛋壳"])),

    item('solid-dairy-products', 'organic', REG,
         ("Produits laitiers", "Dairy products", "乳制品"),
         ("Les produits laitiers vont au bac brun. Evitez d'y verser des liquides comme le lait.",
          "Dairy products go in the brown bin. Avoid pouring in liquids such as milk.",
          "乳制品放入棕桶。避免倒入牛奶等液体。"),
         (["fromage", "beurre", "yogourt"], ["cheese", "butter", "yogurt"], ["奶酪", "黄油", "酸奶"]),
         (["fromage", "beurre", "yogourt", "laitier"], ["cheese", "butter", "yogurt", "dairy"],
          ["奶酪", "黄油", "酸奶", "乳制品"])),

    item('fruit-and-vegetable-scraps', 'organic', REG,
         ("Fruits et legumes", "Fruit and vegetables", "水果和蔬菜"),
         ("Les fruits et legumes, cuisines ou non, vont au bac brun.",
          "Fruit and vegetables, cooked or raw, go in the brown bin.",
          "水果和蔬菜，无论生熟，都放入棕桶。"),
         (["pelures", "trognons", "restes de legumes"], ["peels", "cores", "vegetable scraps"], ["果皮", "果核", "菜叶"]),
         (["fruit", "legume", "pelure", "epluchure"], ["fruit", "vegetable", "peel", "scraps"],
          ["水果", "蔬菜", "果皮"])),

    item('bread-pasta-and-grains', 'organic', REG,
         ("Pain, pates et cereales", "Bread, pasta and grains", "面包、面条和谷物"),
         ("Les cereales, grains, pates et pain vont au bac brun, comme la farine, le sucre et les autres "
          "aliments en poudre.",
          "Cereals, grains, pasta and bread go in the brown bin, as do flour, sugar and other powdered foods.",
          "谷物、米面、面条和面包放入棕桶，面粉、糖等粉状食品也一样。"),
         (["pain", "riz", "pates", "farine"], ["bread", "rice", "pasta", "flour"], ["面包", "米饭", "面条", "面粉"]),
         (["pain", "pates", "riz", "cereales", "farine", "sucre"],
          ["bread", "pasta", "rice", "cereal", "flour", "sugar"],
          ["面包", "面条", "米饭", "谷物", "面粉", "糖"])),

    item('cakes-sweets-and-nuts', 'organic', REG,
         ("Gateaux, sucreries et noix", "Cakes, sweets and nuts", "蛋糕、甜食和坚果"),
         ("Les gateaux, sucreries, noix et ecailles de noix vont au bac brun.",
          "Cakes, sweets, nuts and nutshells go in the brown bin.",
          "蛋糕、甜食、坚果和坚果壳放入棕桶。"),
         (["gateau", "bonbons", "ecailles de noix"], ["cake", "candy", "nutshells"], ["蛋糕", "糖果", "坚果壳"]),
         (["gateau", "bonbon", "noix", "ecaille", "sucrerie"], ["cake", "candy", "nuts", "nutshell", "sweets"],
          ["蛋糕", "糖果", "坚果"])),

    item('tea-and-coffee-grounds', 'organic', REG,
         ("Filtres et residus de cafe et de the", "Coffee grounds, filters and tea", "咖啡渣、滤纸和茶叶"),
         # No cross-reference to coffee capsules here, though it would help a
         # reader: the shared words made "capsules de cafe" return this card
         # instead of the capsule one. The capsule card says where they go.
         ("Les filtres et le cafe moulu, ainsi que les residus de the, vont au bac brun.",
          "Coffee filters and grounds, and tea leaves, go in the brown bin.",
          "咖啡滤纸、咖啡渣和茶叶放入棕桶。"),
         (["marc de cafe", "filtre a cafe", "feuilles de the"], ["coffee grounds", "coffee filter", "tea leaves"],
          ["咖啡渣", "咖啡滤纸", "茶叶"]),
         (["cafe", "filtre", "the", "marc"], ["coffee", "filter", "tea", "grounds"], ["咖啡", "滤纸", "茶叶"])),

    item('pet-food', 'organic', REG,
         ("Nourriture pour animaux", "Pet food", "宠物食品"),
         ("La nourriture pour animaux va au bac brun.",
          "Pet food goes in the brown bin.",
          "宠物食品放入棕桶。"),
         (["croquettes", "nourriture pour chat"], ["kibble", "cat food"], ["猫粮", "狗粮"]),
         # One "food", not three: keyword scores are summed per entry, so
         # "pet food", "dog food" and "cat food" made every question with the
         # word food - "food can", "food containers" - land here.
         (["nourriture animaux", "croquettes", "moulee"], ["pet food", "kibble"],
          ["宠物食品", "猫粮", "狗粮"])),

    item('pet-litter', 'organic', REG,
         ("Litiere", "Pet litter", "猫砂"),
         ("La litiere va au bac brun a Sainte-Therese: le reglement la nomme parmi les matieres organiques "
          "acceptees.",
          "Pet litter goes in the brown bin in Sainte-Therese: the city's regulation lists it among accepted "
          "organics.",
          "在 Sainte-Therese，猫砂放入棕桶：市政条例将其列为可接受的有机垃圾。"),
         (["litiere a chat", "litiere"], ["cat litter", "litter"], ["猫砂", "宠物垫料"]),
         (["litiere", "chat", "excrements"], ["litter", "cat litter", "kitty"], ["猫砂", "猫屎"])),

    item('hair-and-feathers', 'organic', REG,
         ("Cheveux, poils et plumes", "Hair, fur and feathers", "头发、毛发和羽毛"),
         ("Les cheveux, les poils et les plumes vont au bac brun.",
          "Hair, fur and feathers go in the brown bin.",
          "头发、动物毛发和羽毛放入棕桶。"),
         (["cheveux", "poils d'animaux", "plumes"], ["hair", "pet hair", "feathers"], ["头发", "宠物毛", "羽毛"]),
         (["cheveux", "poils", "plumes"], ["hair", "fur", "feathers"], ["头发", "毛发", "羽毛"])),

    item('yard-waste', 'organic', REG,
         ("Residus verts", "Yard waste", "园林垃圾"),
         ("Fleurs, plantes, terre de rempotage, feuilles, rognures de gazon et aiguilles de coniferes vont au "
          "bac brun. Au printemps et a l'automne, la Ville fait aussi des collectes de residus verts: en sacs "
          "de papier ou en contenant avec couvercle, jamais en sacs de plastique.",
          "Flowers, plants, potting soil, leaves, grass clippings and conifer needles go in the brown bin. In "
          "spring and fall the city also runs green-waste collections: in paper bags or a lidded container, "
          "never plastic bags.",
          "花草、盆栽土、落叶、剪下的草和针叶放入棕桶。春秋两季市政府另有园林垃圾收集：请用纸袋或带盖容器，"
          "不收塑料袋。"),
         (["feuilles mortes", "gazon", "fleurs fanees"], ["leaves", "grass clippings", "dead flowers"],
          ["落叶", "草屑", "枯花"]),
         (["feuilles", "gazon", "herbe", "plantes", "fleurs", "aiguilles"],
          ["leaves", "grass", "plants", "flowers", "needles", "garden"], ["落叶", "草", "植物", "花"])),

    item('sawdust-and-wood-chips', 'organic', REG,
         ("Bran de scie et copeaux", "Sawdust and wood chips", "锯末和木屑"),
         ("Le bran de scie, les copeaux, les ecorces et les petites racines vont au bac brun.",
          "Sawdust, wood chips, bark and small roots go in the brown bin.",
          "锯末、木屑、树皮和小根须放入棕桶。"),
         (["bran de scie", "copeaux", "ecorce"], ["sawdust", "wood chips", "bark"], ["锯末", "木屑", "树皮"]),
         (["bran", "scie", "copeaux", "ecorce", "racines"], ["sawdust", "chips", "bark", "roots"],
          ["锯末", "木屑", "树皮"])),

    item('branches', 'organic', PAGE,
         ("Branches", "Branches", "树枝"),
         ("Les petites branches vont au bac brun: le reglement 1373 fixe un maximum de 1 cm de diametre et "
          "60 cm de long. Du 15 avril au 31 octobre, la Ville ramasse gratuitement sur demande les branches "
          "de 4 a 15 cm de diametre et de 3 m au plus. Les branches de 9 pouces et moins sont aussi acceptees "
          "gratuitement a l'ecocentre. Buches, troncs, souches et racines ne sont pas ramasses.",
          "Small branches go in the brown bin: regulation 1373 sets a maximum of 1 cm across and 60 cm long. "
          "From 15 April to 31 October the city collects branches 4 to 15 cm across and up to 3 m long, free, "
          "on request. Branches of 9 inches across or less are also free at the ecocentre. Logs, trunks, stumps "
          "and roots are not collected.",
          "小树枝放入棕桶：1373 号条例规定直径不超过 1 厘米、长度不超过 60 厘米。4 月 15 日至 10 月 31 日，"
          "市政府可应申请免费收集直径 4-15 厘米、长度不超过 3 米的树枝。直径 9 英寸以内的树枝也可免费送到"
          "生态中心。原木、树干、树桩和树根不收。"),
         (["petites branches", "branches elaguees"], ["small branches", "pruned branches"], ["小树枝", "修剪的树枝"]),
         (["branche", "elagage", "ramassage"], ["branch", "branches", "pruning"], ["树枝", "修剪"]),
         availability=("Ramassage sur demande du 15 avril au 31 octobre, delai maximal de 15 jours ouvrables.",
                       "Pickup on request from 15 April to 31 October, within 15 working days.",
                       "4 月 15 日至 10 月 31 日可申请上门收集，最长 15 个工作日内完成。")),

    item('tissues-and-paper-napkins', 'organic', REG,
         ("Mouchoirs, serviettes et essuie-tout", "Tissues, napkins and paper towels", "纸巾、餐巾和厨房纸"),
         ("Les serviettes de table, le papier essuie-tout et les papiers mouchoirs vont au bac brun. "
          "Un essuie-tout souille de produits de nettoyage va au bac noir.",
          "Napkins, paper towels and tissues go in the brown bin. A paper towel soiled with cleaning products "
          "goes in the black bin.",
          "餐巾、厨房纸和纸巾放入棕桶。沾了清洁剂的厨房纸放入黑桶。"),
         (["mouchoirs", "serviettes de table", "essuie-tout"], ["tissues", "napkins", "paper towel"],
          ["纸巾", "餐巾纸", "厨房纸"]),
         (["mouchoir", "serviette", "essuie-tout"], ["tissue", "napkin", "paper towel"], ["纸巾", "餐巾", "厨房纸"])),

    item('food-soiled-paper-and-cardboard', 'organic', REG,
         ("Papiers et cartons souilles d aliments", "Food-soiled paper and cardboard", "沾有食物的纸和纸板"),
         ("Les papiers et cartons souilles par des aliments vont au bac brun: assiettes de carton, boites de "
          "pizza, papier qui a servi a emballer des aliments.",
          "Paper and cardboard soiled by food go in the brown bin: paper plates, pizza boxes, paper used to "
          "wrap food.",
          "沾有食物的纸和纸板放入棕桶：纸盘、披萨盒、包过食物的纸。"),
         (["boite de pizza", "assiette de carton"], ["pizza box", "paper plate"], ["披萨盒", "纸盘"]),
         (["pizza", "carton souille", "assiette carton"], ["pizza", "soiled cardboard", "paper plate"],
          ["披萨盒", "纸盘", "脏纸板"])),

    item('toothpicks-and-wooden-sticks', 'organic', REG,
         ("Batons de bois, cure-dents et bouchons de liege", "Wooden sticks, toothpicks and corks",
          "木棒、牙签和软木塞"),
         ("Les batons de bois, les cure-dents et les bouchons de liege vont au bac brun.",
          "Wooden sticks, toothpicks and corks go in the brown bin.",
          "木棒、牙签和软木塞放入棕桶。"),
         (["cure-dents", "baton de popsicle", "bouchon de liege"], ["toothpicks", "popsicle stick", "wine cork"],
          ["牙签", "冰棍棒", "软木塞"]),
         (["cure-dent", "baton", "liege", "bouchon"], ["toothpick", "stick", "cork"], ["牙签", "木棒", "软木塞"])),

    item('sand-and-soil', 'organic', REG,
         ("Sable et terre", "Sand and soil", "沙子和泥土"),
         ("De petites quantites de sable et de terre vont au bac brun. Pour de grandes quantites, la terre et "
          "les deblais vont a l'ecocentre; ils sont refuses a la collecte des encombrants.",
          "Small amounts of sand and soil go in the brown bin. Large amounts of soil and fill go to the "
          "ecocentre; the bulky pickup refuses them.",
          "少量沙子和泥土放入棕桶。大量泥土和渣土送到生态中心；大件垃圾收集不收。"),
         (["terre de jardin", "sable"], ["garden soil", "sand"], ["花园土", "沙子"]),
         (["sable", "terre"], ["sand", "soil", "dirt"], ["沙子", "泥土"])),

    item('compostable-or-biodegradable-dishes-and-bags', 'garbage', PAGE,
         ("Sacs compostables ou biodegradables", "Compostable or biodegradable bags", "可堆肥或可降解塑料袋"),
         ("Les sacs de plastique, meme biodegradables ou compostables, sont interdits dans le bac brun: seuls "
          "les sacs de papier y sont acceptes. Le bac bleu exclut aussi les plastiques degradables. Ils vont "
          "donc au bac noir.",
          "Plastic bags, even biodegradable or compostable ones, are banned from the brown bin: only paper "
          "bags are accepted there. The blue bin excludes degradable plastics too. So they go in the black bin.",
          "塑料袋即使是可降解或可堆肥的，也不能放入棕桶：棕桶只接受纸袋。蓝桶同样不收可降解塑料。"
          "因此请放入黑桶。"),
         (["sac compostable", "sac biodegradable"], ["compostable bag", "biodegradable bag"],
          ["可堆肥袋", "可降解袋"]),
         (["compostable", "biodegradable", "oxobiodegradable"], ["compostable", "biodegradable", "bioplastic"],
          ["可堆肥", "可降解", "生物降解"])),

    # ------------------------------------------------------------------
    # Bac bleu - Annexe B
    # ------------------------------------------------------------------
    item('printed-paper', 'recycling', REG,
         ("Papier et imprimes", "Paper and printed matter", "纸张和印刷品"),
         ("Journaux, circulaires, revues, magazines, catalogues, feuilles et enveloppes vont au bac bleu.",
          "Newspapers, flyers, magazines, catalogues, sheets of paper and envelopes go in the blue bin.",
          "报纸、传单、杂志、目录、纸张和信封放入蓝桶。"),
         (["journaux", "circulaires", "enveloppes"], ["newspapers", "flyers", "envelopes"], ["报纸", "传单", "信封"]),
         (["journal", "circulaire", "revue", "magazine", "enveloppe", "papier"],
          ["newspaper", "flyer", "magazine", "envelope", "paper", "mail"], ["报纸", "杂志", "信封", "纸"])),

    item('shredded-paper', 'recycling', REG,
         ("Papier dechiquete", "Shredded paper", "碎纸"),
         ("Le papier dechiquete va au bac bleu.",
          "Shredded paper goes in the blue bin.",
          "碎纸放入蓝桶。"),
         (["papier dechiquete"], ["shredded paper"], ["碎纸"]),
         (["dechiquete", "dechiqueteuse"], ["shredded", "shredder"], ["碎纸", "碎纸机"])),

    item('cardboard-boxes', 'recycling', REG,
         ("Boites de carton", "Cardboard boxes", "纸箱"),
         ("Les boites de carton ondule, plat ou lamine et les rouleaux de carton vont au bac bleu. Le carton "
          "n'est plus accepte a la collecte des encombrants; deux collectes de surplus de carton ont lieu "
          "chaque annee.",
          "Corrugated, flat or laminated cardboard boxes and cardboard tubes go in the blue bin. The bulky "
          "pickup no longer takes cardboard; there are two cardboard surplus collections a year.",
          "瓦楞纸箱、平板或覆膜纸盒以及纸筒放入蓝桶。大件垃圾收集不再收纸板；每年有两次纸板加量收集。"),
         (["boite de cereales", "carton de livraison", "rouleau de papier"],
          ["cereal box", "shipping box", "paper towel roll"], ["麦片盒", "快递纸箱", "卷纸芯"]),
         (["carton", "boite", "rouleau"], ["cardboard", "box", "tube"], ["纸箱", "纸盒", "纸板"])),

    item('egg-carton-and-fruit-basket', 'recycling', REG,
         ("Boite d'oeufs", "Egg carton", "鸡蛋盒"),
         ("Les boites d'oeufs vont au bac bleu, en carton comme en polystyrene.",
          "Egg cartons go in the blue bin, cardboard and polystyrene alike.",
          "鸡蛋盒放入蓝桶，纸质和泡沫的都可以。"),
         (["boite d'oeufs"], ["egg carton"], ["鸡蛋盒"]),
         (["boite oeufs"], ["egg carton", "egg box"], ["鸡蛋盒", "蛋托"])),

    item('paper-bag', 'recycling', REG,
         ("Sac de papier", "Paper bag", "纸袋"),
         ("Les sacs de papier, plastifies ou non, vont au bac bleu.",
          "Paper bags, coated or not, go in the blue bin.",
          "纸袋，无论是否有覆膜，都放入蓝桶。"),
         (["sac d'epicerie en papier"], ["paper grocery bag"], ["纸质购物袋"]),
         (["sac papier"], ["paper bag"], ["纸袋"])),

    item('milk-and-juice-cartons', 'recycling', REG,
         ("Contenants de lait et de jus", "Milk and juice cartons", "牛奶盒和果汁盒"),
         ("Les contenants a pignon et les contenants aseptiques de type Tetra Pak vont au bac bleu, comme les "
          "contenants de carton a fond et couvercle de metal ou de plastique.",
          "Gable-top cartons and Tetra Pak-style aseptic cartons go in the blue bin, as do cardboard "
          "containers with a metal or plastic bottom and lid.",
          "屋顶型纸盒和利乐包类无菌纸盒放入蓝桶，带金属或塑料底和盖的纸筒容器也一样。"),
         (["contenant de lait", "boite de jus", "tetra pak"], ["milk carton", "juice box", "tetra pak"],
          ["牛奶盒", "果汁盒", "利乐包"]),
         (["lait", "jus", "tetra", "pignon"], ["milk", "juice", "carton", "tetra"], ["牛奶盒", "果汁盒", "利乐包"])),

    item('containers-and-packaging', 'recycling', REG,
         ("Bouteilles et contenants de plastique", "Plastic bottles and containers", "塑料瓶和塑料容器"),
         ("Les bouteilles, contenants et emballages de plastique no 1, 2, 3, 4 et 5 vont au bac bleu: "
          "aliments, boissons, cosmetiques, hygiene et produits d'entretien.",
          "Plastic bottles, containers and packaging marked 1, 2, 3, 4 or 5 go in the blue bin: food, drinks, "
          "cosmetics, toiletries and cleaning products.",
          "标有 1、2、3、4、5 号的塑料瓶、容器和包装放入蓝桶：食品、饮料、化妆品、个人护理和清洁用品均可。"),
         (["bouteille d'eau", "pot de yogourt", "bouteille de detergent"],
          ["water bottle", "yogurt tub", "detergent bottle"], ["水瓶", "酸奶杯", "洗涤剂瓶"]),
         (["plastique", "bouteille", "contenant", "emballage"], ["plastic", "bottle", "container", "packaging"],
          ["塑料瓶", "塑料容器", "塑料"])),

    item('shampoo-bottle', 'recycling', REG,
         ("Bouteille de shampoing", "Shampoo bottle", "洗发水瓶"),
         ("Les bouteilles de shampoing et autres produits d'hygiene en plastique no 1 a 5 vont au bac bleu.",
          "Shampoo bottles and other toiletry bottles in plastic 1 to 5 go in the blue bin.",
          "1 至 5 号塑料的洗发水瓶和其他个人护理用品瓶放入蓝桶。"),
         (["bouteille de shampoing", "flacon de savon"], ["shampoo bottle", "soap bottle"], ["洗发水瓶", "洗手液瓶"]),
         (["shampoing", "savon", "hygiene"], ["shampoo", "soap", "toiletry"], ["洗发水", "沐浴露"])),

    item('plastic-bags-and-film', 'recycling', REG,
         ("Sacs et pellicules de plastique", "Plastic bags and film", "塑料袋和保鲜膜"),
         ("Les sacs et pellicules d'emballage en plastique, les plastiques souples et la pellicule extensible "
          "vont au bac bleu. Les plastiques degradables en sont exclus.",
          "Plastic bags, wrapping film, soft plastics and stretch wrap go in the blue bin. Degradable plastics "
          "are excluded.",
          "塑料袋、包装膜、软塑料和拉伸膜放入蓝桶。可降解塑料除外。"),
         (["sac d'epicerie", "pellicule d'emballage", "sac de pain"], ["grocery bag", "plastic wrap", "bread bag"],
          ["购物袋", "包装膜", "面包袋"]),
         (["sac plastique", "pellicule", "emballage souple"], ["plastic bag", "film", "wrap", "soft plastic"],
          ["塑料袋", "保鲜膜", "软塑料"])),

    item('stand-up-pouches', 'recycling', REG,
         ("Sachets autoportants", "Stand-up pouches", "自立袋"),
         ("Les sachets autoportants vont au bac bleu.",
          "Stand-up pouches go in the blue bin.",
          "自立袋放入蓝桶。"),
         (["sachet de compote", "sachet refermable"], ["fruit pouch", "resealable pouch"], ["果泥袋", "自封袋"]),
         (["sachet", "autoportant"], ["pouch", "stand-up"], ["自立袋", "包装袋"])),

    item('styrofoam-food-containers', 'recycling', REG,
         ("Contenants alimentaires en styromousse", "Styrofoam food containers", "泡沫餐盒"),
         ("Les emballages et contenants alimentaires en polystyrene (no 6), expanse ou rigide, vont au bac bleu "
          "a Sainte-Therese: barquettes de viande, verres a cafe, contenants de mets a emporter. "
          "L'emballage de protection en styromousse, lui, va a l'ecocentre.",
          "Polystyrene (number 6) food packaging and containers, foam or rigid, go in the blue bin in "
          "Sainte-Therese: meat trays, coffee cups, take-out containers. Protective foam packaging goes to "
          "the ecocentre instead.",
          "在 Sainte-Therese，6 号聚苯乙烯食品包装和容器（发泡或硬质）放入蓝桶：肉托盘、咖啡杯、外卖餐盒。"
          "保护用泡沫包装则送到生态中心。"),
         (["barquette de viande", "verre a cafe en styromousse", "contenant pour emporter"],
          ["meat tray", "foam coffee cup", "take-out container"], ["肉托盘", "泡沫咖啡杯", "外卖餐盒"]),
         (["styromousse", "polystyrene", "barquette"],
          ["styrofoam", "styrofoam container", "polystyrene", "foam", "tray"],
          ["泡沫", "聚苯乙烯", "泡沫餐盒"])),

    item('plastic-number-7', 'recycling', REG,
         # "no" and "7" are both too short to index, so "Plastique no 7"
         # searched as plain "plastique" and found the plastic bottles.
         ("Plastique numero 7", "Plastic number 7", "7 号塑料"),
         ("Les autres plastiques (no 7) vont au bac bleu, sauf les plastiques degradables.",
          "Other plastics (number 7) go in the blue bin, except degradable plastics.",
          "其他塑料（7 号）放入蓝桶，可降解塑料除外。"),
         (["contenant no 7"], ["number 7 container"], ["7 号塑料容器"]),
         # "no" and "7" are too short to index; "numero" and the singular
         # "plastique" are what a search for this entry can actually match.
         (["numero", "plastique numero", "autres plastiques"], ["number 7", "number", "other plastics"],
          ["7号塑料", "其他塑料"])),

    item('coffee-capsules', 'recycling', REG,
         ("Capsules de cafe", "Coffee capsules", "咖啡胶囊"),
         ("Les capsules de cafe et de the en plastique no 5 ou no 6, et les capsules d'aluminium, vont au bac "
          "bleu.",
          "Coffee and tea capsules in plastic 5 or 6, and aluminium capsules, go in the blue bin.",
          "5 号或 6 号塑料的咖啡和茶胶囊，以及铝制胶囊，放入蓝桶。"),
         (["capsule de cafe", "dosette"], ["coffee pod", "capsule"], ["咖啡胶囊", "咖啡粉囊"]),
         # Plurals too: the full-text index does not stem, so "capsule" never
         # matches a resident's "capsules".
         (["capsule", "capsules", "dosette", "dosettes", "keurig", "nespresso"],
          ["capsule", "capsules", "pod", "pods", "keurig", "nespresso"],
          ["咖啡胶囊", "胶囊"])),

    item('food-can', 'recycling', REG,
         # Not "Food can": the query normaliser strips "can" as the verb it
         # usually is, so the name searched as plain "food".
         ("Boite de conserve", "Tin cans", "罐头盒"),
         ("Les boites de conserve et autres contenants en acier vont au bac bleu. Les contenants sous pression "
          "(aerosols) en sont exclus.",
          "Food cans and other steel containers go in the blue bin. Pressurised containers (aerosols) are "
          "excluded.",
          "罐头盒和其他钢制容器放入蓝桶。压力容器（气雾罐）除外。"),
         (["conserve", "boite de soupe"], ["tin can", "soup can"], ["罐头", "汤罐"]),
         (["conserve", "boite de conserve", "acier", "metal"], ["tin can", "cans", "tin", "steel", "metal"],
          ["罐头", "铁罐"])),

    item('aluminium-cans-and-foil', 'recycling', REG,
         ("Aluminium: canettes, papier et assiettes", "Aluminium cans, foil and plates", "铝罐、铝箔和铝盘"),
         ("Les canettes, le papier et les assiettes d'aluminium vont au bac bleu, sauf les contenants sous "
          "pression.",
          "Aluminium cans, foil and plates go in the blue bin, except pressurised containers.",
          "铝罐、铝箔和铝盘放入蓝桶，压力容器除外。"),
         (["canette", "papier d'aluminium", "assiette d'aluminium"], ["pop can", "tin foil", "foil plate"],
          ["易拉罐", "锡纸", "铝盘"]),
         (["aluminium", "canette", "papier aluminium"], ["aluminium", "aluminum", "can", "foil"],
          ["易拉罐", "铝箔", "锡纸"])),

    item('metal-hangers', 'recycling', REG,
         ("Cintres metalliques", "Metal hangers", "金属衣架"),
         ("Les cintres metalliques vont au bac bleu.",
          "Metal coat hangers go in the blue bin.",
          "金属衣架放入蓝桶。"),
         (["cintre"], ["coat hanger"], ["衣架"]),
         (["cintre"], ["hanger", "coat hanger"], ["衣架"])),

    item('wine-bottle', 'recycling', REG,
         ("Bouteilles et pots de verre", "Glass bottles and jars", "玻璃瓶和玻璃罐"),
         ("Les contenants et bouteilles en verre vont au bac bleu. Le verre qui n'est pas un contenant "
          "alimentaire (vitre, vaisselle, miroir) va au bac noir.",
          "Glass containers and bottles go in the blue bin. Glass that is not a food container (window glass, "
          "dishes, mirrors) goes in the black bin.",
          "玻璃容器和玻璃瓶放入蓝桶。非食品容器的玻璃（窗玻璃、餐具、镜子）放入黑桶。"),
         (["bouteille de vin", "pot de confiture"], ["wine bottle", "jam jar"], ["酒瓶", "果酱瓶"]),
         (["verre", "bouteille", "vin", "pot"], ["glass", "bottle", "wine", "jar"], ["玻璃瓶", "酒瓶", "玻璃罐"])),

    item('aerosol-cans', 'ecocentre', REG,
         ("Aerosols", "Aerosol cans", "气雾罐"),
         ("Les contenants sous pression (aerosols) sont exclus du bac bleu. Ce sont des residus domestiques "
          "dangereux: apportez-les a l'ecocentre.",
          "Pressurised containers (aerosols) are excluded from the blue bin. They are household hazardous "
          "waste: take them to the ecocentre.",
          "压力容器（气雾罐）不能放入蓝桶。它们属于家庭危险废物，请送到生态中心。"),
         (["bombe aerosol", "fixatif", "peinture en aerosol"], ["spray can", "hairspray", "spray paint"],
          ["喷雾罐", "发胶", "喷漆"]),
         (["aerosol", "bombe", "vaporisateur"], ["aerosol", "spray", "spray can"], ["喷雾", "气雾罐"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    # ------------------------------------------------------------------
    # Bac noir - Annexe A
    # ------------------------------------------------------------------
    item('diapers-and-hygiene-products', 'garbage', REG,
         ("Couches et produits hygieniques", "Diapers and hygiene products", "尿布和卫生用品"),
         ("Les couches et les produits hygieniques vont au bac noir.",
          "Diapers and hygiene products go in the black bin.",
          "尿布和卫生用品放入黑桶。"),
         (["couche", "serviette hygienique", "tampon"], ["diaper", "sanitary pad", "tampon"],
          ["尿布", "卫生巾", "卫生棉条"]),
         (["couche", "hygienique", "tampon"], ["diaper", "nappy", "sanitary", "tampon"], ["尿布", "卫生巾"])),

    item('broken-dishes', 'garbage', REG,
         ("Vaisselle et verre non alimentaire", "Dishes and non-food glass", "餐具和非食品玻璃"),
         ("La vaisselle, la porcelaine, les ustensiles et le verre qui ne vient pas d'un contenant alimentaire "
          "vont au bac noir.",
          "Dishes, porcelain, cutlery and glass that is not from a food container go in the black bin.",
          "餐具、瓷器、刀叉以及非食品容器的玻璃放入黑桶。"),
         (["assiette cassee", "tasse", "verre a boire"], ["broken plate", "mug", "drinking glass"],
          ["碎盘子", "杯子", "玻璃杯"]),
         (["vaisselle", "porcelaine", "ustensile", "tasse"], ["dishes", "porcelain", "cutlery", "mug"],
          ["餐具", "瓷器", "碗碟"])),

    item('broken-glass-or-mirror', 'garbage', REG,
         ("Miroir", "Mirror", "镜子"),
         ("A Sainte-Therese, les miroirs vont au bac noir: le reglement les nomme parmi les dechets ultimes. "
          "Un miroir trop grand pour le bac est un encombrant.",
          "In Sainte-Therese, mirrors go in the black bin: the regulation lists them as final waste. A mirror "
          "too big for the bin is a bulky item.",
          "在 Sainte-Therese，镜子放入黑桶：条例将其列为最终废物。放不进桶的大镜子按大件垃圾处理。"),
         (["miroir casse"], ["broken mirror"], ["碎镜子"]),
         (["miroir"], ["mirror"], ["镜子"])),

    item('photos', 'garbage', REG,
         ("Photos", "Photos", "照片"),
         ("Les photos vont au bac noir, pas au bac bleu.",
          "Photographs go in the black bin, not the blue one.",
          "照片放入黑桶，不要放入蓝桶。"),
         (["photo imprimee"], ["printed photo"], ["冲印照片"]),
         (["photo", "photos", "photographie"], ["photo", "photos", "photograph", "picture", "pictures"],
          ["照片", "相片"])),

    item('rubber', 'garbage', REG,
         ("Caoutchouc", "Rubber", "橡胶"),
         ("Le caoutchouc va au bac noir. Les pneus, eux, vont a l'ecocentre.",
          "Rubber goes in the black bin. Tires go to the ecocentre.",
          "橡胶制品放入黑桶。轮胎则送到生态中心。"),
         (["gant de caoutchouc", "elastique"], ["rubber glove", "rubber band"], ["橡胶手套", "橡皮筋"]),
         (["caoutchouc", "elastique"], ["rubber", "rubber band"], ["橡胶", "橡皮筋"])),

    item('cds-and-cassettes', 'garbage', REG,
         ("Disques compacts et cassettes", "CDs and cassettes", "光盘和磁带"),
         ("Les disques compacts, cassettes audio et cassettes video vont au bac noir.",
          "Compact discs, audio cassettes and video cassettes go in the black bin.",
          "光盘、录音带和录像带放入黑桶。"),
         (["CD", "DVD", "cassette VHS"], ["CD", "DVD", "VHS tape"], ["光盘", "DVD", "录像带"]),
         (["disque", "cassette", "dvd"], ["disc", "cassette", "dvd", "vhs"], ["光盘", "磁带", "录像带"])),

    item('dryer-lint-and-vacuum-bags', 'garbage', REG,
         ("Charpie, assouplisseur et sacs d'aspirateur", "Dryer lint, dryer sheets and vacuum bags",
          "烘干机绒毛、烘衣纸和吸尘袋"),
         ("Les feuilles d'assouplisseur, la charpie de secheuse et les sacs d'aspirateur avec leur contenu "
          "vont au bac noir.",
          "Dryer sheets, dryer lint and vacuum bags with their contents go in the black bin.",
          "烘衣纸、烘干机绒毛和吸尘袋（连同内容物）放入黑桶。"),
         (["charpie", "feuille d'assouplisseur", "sac d'aspirateur"], ["lint", "dryer sheet", "vacuum bag"],
          ["绒毛", "烘衣纸", "吸尘袋"]),
         (["charpie", "assouplisseur", "aspirateur", "poussiere"], ["lint", "dryer sheet", "vacuum", "dust"],
          ["绒毛", "烘衣纸", "吸尘器", "灰尘"])),

    item('paper-towel-with-chemicals-on-it', 'garbage', REG,
         ("Essuie-tout souille de produits de nettoyage", "Paper towel with cleaning products on it",
          "沾有清洁剂的厨房纸"),
         ("Un essuie-tout souille par des produits de nettoyage va au bac noir, pas au bac brun.",
          "A paper towel soiled with cleaning products goes in the black bin, not the brown one.",
          "沾有清洁剂的厨房纸放入黑桶，不要放入棕桶。"),
         (["essuie-tout avec nettoyant"], ["paper towel with cleaner"], ["沾清洁剂的纸"]),
         (["essuie-tout", "nettoyant", "produit chimique"], ["paper towel", "cleaner", "chemical"],
          ["厨房纸", "清洁剂"])),

    item('cotton-swabs-and-floss', 'garbage', REG,
         ("Ouates, cure-oreilles et soie dentaire", "Cotton pads, cotton swabs and dental floss",
          "化妆棉、棉签和牙线"),
         ("Les ouates, les cure-oreilles en plastique et la soie dentaire vont au bac noir.",
          "Cotton pads, plastic cotton swabs and dental floss go in the black bin.",
          "化妆棉、塑料棉签和牙线放入黑桶。"),
         (["coton-tige", "tampon d'ouate", "soie dentaire"], ["cotton swab", "cotton pad", "floss"],
          ["棉签", "化妆棉", "牙线"]),
         (["ouate", "coton", "cure-oreille", "soie dentaire"], ["cotton", "swab", "q-tip", "floss"],
          ["棉签", "化妆棉", "牙线"])),

    item('candles', 'garbage', REG,
         ("Chandelles", "Candles", "蜡烛"),
         ("Les chandelles vont au bac noir.",
          "Candles go in the black bin.",
          "蜡烛放入黑桶。"),
         (["chandelle", "bougie"], ["candle", "tealight"], ["蜡烛"]),
         (["chandelle", "bougie", "cire"], ["candle", "wax", "tealight"], ["蜡烛", "蜡"])),

    # ------------------------------------------------------------------
    # Ecocentre - Annexe E and the ecocentre page
    # ------------------------------------------------------------------
    item('light-bulb', 'ecocentre', REG,
         ("Ampoules et tubes fluorescents", "Light bulbs and fluorescent tubes", "灯泡和荧光灯管"),
         ("A Sainte-Therese, les ampoules, les fluocompactes et les tubes fluorescents sont des residus "
          "domestiques dangereux: apportez-les a l'ecocentre.",
          "In Sainte-Therese, light bulbs, compact fluorescents and fluorescent tubes are household hazardous "
          "waste: take them to the ecocentre.",
          "在 Sainte-Therese，灯泡、节能灯和荧光灯管属于家庭危险废物，请送到生态中心。"),
         (["ampoule", "fluocompacte", "tube fluorescent"], ["light bulb", "CFL", "fluorescent tube"],
          ["灯泡", "节能灯", "荧光灯管"]),
         (["ampoule", "fluocompacte", "fluorescent", "neon"], ["bulb", "light bulb", "fluorescent", "cfl"],
          ["灯泡", "节能灯", "灯管"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('paint', 'ecocentre', REG,
         ("Peinture et solvants", "Paint and solvents", "油漆和溶剂"),
         ("La peinture, les solvants, les acides et les nettoyants sont des residus domestiques dangereux: "
          "apportez-les a l'ecocentre. Jamais au bac noir.",
          "Paint, solvents, acids and cleaners are household hazardous waste: take them to the ecocentre. "
          "Never in the black bin.",
          "油漆、溶剂、酸碱和清洁剂属于家庭危险废物，请送到生态中心，切勿放入黑桶。"),
         (["pot de peinture", "diluant", "produit de piscine"], ["paint can", "thinner", "pool chemicals"],
          ["油漆桶", "稀释剂", "泳池化学品"]),
         (["peinture", "solvant", "diluant", "piscine", "nettoyant"],
          ["paint", "solvent", "thinner", "pool", "chemical"], ["油漆", "溶剂", "化学品"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('batteries', 'ecocentre', ECO,
         ("Piles et batteries", "Batteries", "电池"),
         ("Les piles et batteries vont a l'ecocentre, comme les produits qui fonctionnent avec des piles. "
          "Regroupez les piles au lithium dans un sac, separees des autres.",
          "Batteries go to the ecocentre, as do battery-powered products. Put lithium batteries in a bag of "
          "their own, apart from the others.",
          "电池以及用电池驱动的产品送到生态中心。锂电池请单独装袋，与其他电池分开。"),
         (["piles AA", "batterie au lithium", "vapoteuse"], ["AA batteries", "lithium battery", "vape"],
          ["5 号电池", "锂电池", "电子烟"]),
         (["pile", "batterie", "lithium", "vapoteuse"], ["battery", "batteries", "lithium", "vape"],
          ["电池", "废电池", "锂电池"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('used-cooking-oil', 'ecocentre', REG,
         ("Huile de cuisson usagee", "Used cooking oil", "废食用油"),
         ("L'huile de cuisson usagee va a l'ecocentre, avec l'huile a moteur. Ne la versez pas dans le bac brun.",
          "Used cooking oil goes to the ecocentre, with motor oil. Do not pour it into the brown bin.",
          "废食用油和机油一样送到生态中心，不要倒入棕桶。"),
         (["huile de friture", "huile a moteur"], ["frying oil", "motor oil"], ["炸油", "机油"]),
         (["huile", "friture", "moteur"], ["oil", "cooking oil", "frying", "motor oil"], ["废油", "食用油", "机油"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('propane-tanks', 'ecocentre', REG,
         ("Bonbonnes de propane", "Propane tanks", "丙烷气罐"),
         ("Les bonbonnes de propane vont a l'ecocentre.",
          "Propane tanks go to the ecocentre.",
          "丙烷气罐送到生态中心。"),
         (["bonbonne de BBQ"], ["BBQ tank"], ["烧烤气罐"]),
         (["propane", "bonbonne"], ["propane", "tank", "cylinder"], ["丙烷", "气罐"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('electronics', 'ecocentre', REG,
         ("Appareils electroniques et informatiques", "Electronics and computers", "电子和电脑设备"),
         ("Les appareils electroniques et informatiques vont gratuitement a l'ecocentre. Ils sont refuses a "
          "la collecte des encombrants.",
          "Electronics and computer equipment go to the ecocentre, free. The bulky pickup refuses them.",
          "电子和电脑设备可免费送到生态中心。大件垃圾收集不收。"),
         (["ordinateur", "televiseur", "cellulaire"], ["computer", "television", "cell phone"],
          ["电脑", "电视", "手机"]),
         (["electronique", "ordinateur", "televiseur", "cellulaire", "imprimante"],
          ["electronics", "computer", "television", "phone", "printer"], ["电子产品", "电脑", "电视", "手机"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('construction-debris', 'ecocentre', ECO,
         ("Materiaux de construction et renovation", "Construction and renovation debris", "建筑和装修垃圾"),
         ("Depuis 2026, les materiaux de construction, renovation et demolition ne sont plus acceptes a la "
          "collecte des encombrants. Ils sont acceptes gratuitement a l'ecocentre jusqu'a 10 metres cubes par "
          "annee par adresse.",
          "Since 2026, construction, renovation and demolition materials are no longer taken by the bulky "
          "pickup. The ecocentre accepts them free, up to 10 cubic metres a year per address.",
          "自 2026 年起，大件垃圾收集不再收建筑、装修和拆除材料。生态中心免费接收，每个地址每年最多 10 立方米。"),
         (["gypse", "bois de construction", "bardeaux"], ["drywall", "lumber", "shingles"], ["石膏板", "木料", "瓦片"]),
         (["construction", "renovation", "gypse", "beton", "brique", "bardeau"],
          ["construction", "renovation", "drywall", "concrete", "brick", "shingle"],
          ["建筑垃圾", "装修垃圾", "石膏板", "砖"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('toilet-and-sink', 'ecocentre', PAGE,
         ("Toilette et lavabo", "Toilet and sink", "马桶和洗手盆"),
         ("Les toilettes et les lavabos sont refuses a la collecte des encombrants: apportez-les a l'ecocentre, "
          "avec les materiaux de renovation.",
          "Toilets and sinks are refused by the bulky pickup: take them to the ecocentre, with renovation "
          "materials.",
          "大件垃圾收集不收马桶和洗手盆，请与装修材料一起送到生态中心。"),
         (["toilette", "lavabo", "evier"], ["toilet", "bathroom sink", "sink"], ["马桶", "洗手盆", "水槽"]),
         (["toilette", "lavabo", "evier"], ["toilet", "sink", "basin"], ["马桶", "洗手盆"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('tires', 'ecocentre', ECO,
         ("Pneus", "Tires", "轮胎"),
         ("Les pneus uses, sans jante, vont a l'ecocentre, comme les pneus de velo et les chambres a air.",
          "Used tires, off the rim, go to the ecocentre, as do bicycle tires and inner tubes.",
          "废旧轮胎（拆下轮毂）送到生态中心，自行车轮胎和内胎也一样。"),
         (["pneu d'auto", "pneu de velo"], ["car tire", "bike tire"], ["汽车轮胎", "自行车轮胎"]),
         (["pneu", "chambre a air"], ["tire", "tyre", "inner tube"], ["轮胎", "内胎"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('scrap-metal', 'ecocentre', REG,
         ("Metal", "Scrap metal", "废金属"),
         ("Le fer, l'aluminium, le cuivre, les fils, les gouttieres, les barbecues, les bases de lit et les "
          "bicyclettes vont a l'ecocentre.",
          "Iron, aluminium, copper, wire, gutters, barbecues, bed frames and bicycles go to the ecocentre.",
          "铁、铝、铜、电线、檐槽、烧烤炉、床架和自行车送到生态中心。"),
         (["velo", "barbecue", "base de lit"], ["bicycle", "barbecue", "bed frame"], ["自行车", "烧烤炉", "床架"]),
         (["metal", "fer", "cuivre", "velo", "barbecue", "bbq"], ["metal", "iron", "copper", "bicycle", "bbq"],
          ["废金属", "自行车", "烧烤炉"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    item('styrofoam-protective-packaging', 'ecocentre', REG,
         ("Styromousse de protection", "Protective styrofoam packaging", "保护用泡沫包装"),
         ("Le styromousse de protection (emballage d'appareils, caissettes de vegetaux) est exclu du bac bleu: "
          "nettoye et trie, il va a l'ecocentre. Les contenants alimentaires en styromousse, eux, vont au bac "
          "bleu.",
          "Protective foam (appliance packaging, plant trays) is excluded from the blue bin: cleaned and "
          "sorted, it goes to the ecocentre. Foam food containers go in the blue bin.",
          "保护用泡沫（电器包装、育苗托盘）不能放入蓝桶，清洁分类后送到生态中心。泡沫食品容器则放入蓝桶。"),
         (["styromousse de television", "bloc de protection"], ["TV foam", "foam packing block"],
          ["电视泡沫", "泡沫包装块"]),
         (["styromousse", "protection", "emballage appareil"], ["styrofoam", "packing foam", "protective"],
          ["泡沫", "包装泡沫"]),
         location=ECO_LOCATION, availability=ECO_HOURS),

    # ------------------------------------------------------------------
    # Bulky pickup and special collections - the collections page
    # ------------------------------------------------------------------
    item('bulky-items', 'bulky', PAGE,
         ("Encombrants", "Bulky items", "大件垃圾"),
         ("Les encombrants sont ramasses le premier lundi du mois, partout: meubles, matelas, fauteuils, "
          "electromenagers non refrigerants, chauffe-eau, filtres de piscine sans le sable. Maximum 2,5 m et "
          "25 kg par objet. Refuses: carton, materiaux de construction, electroniques, appareils refrigerants, "
          "pneus, residus dangereux.",
          "Bulky items are picked up on the first Monday of the month, everywhere: furniture, mattresses, "
          "armchairs, non-refrigerating appliances, water heaters, pool filters without sand. At most 2.5 m "
          "and 25 kg per item. Refused: cardboard, construction materials, electronics, refrigerating "
          "appliances, tires, hazardous waste.",
          "大件垃圾每月第一个周一全市收集：家具、床垫、沙发、非制冷家电、热水器、去沙的泳池过滤器。每件最长 "
          "2.5 米、最重 25 公斤。不收：纸板、建筑材料、电子产品、制冷电器、轮胎、危险废物。"),
         (["matelas", "sofa", "chaise", "laveuse"], ["mattress", "sofa", "chair", "washing machine"],
          ["床垫", "沙发", "椅子", "洗衣机"]),
         (["encombrant", "meuble", "matelas", "divan", "chauffe-eau", "laveuse", "secheuse"],
          ["bulky", "furniture", "mattress", "couch", "water heater", "washer", "dryer"],
          ["大件垃圾", "家具", "床垫", "沙发", "热水器"]),
         availability=("Premier lundi du mois. En bordure de rue des 18 h la veille ou avant 7 h le jour meme.",
                       "First Monday of the month. At the curb from 6 p.m. the day before or by 7 a.m. that day.",
                       "每月第一个周一。前一天 18 点后或当天 7 点前放到路边。")),

    item('refrigerating-appliances', 'special', PAGE,
         ("Refrigerateur, congelateur et climatiseur", "Fridge, freezer and air conditioner",
          "冰箱、冰柜和空调"),
         ("Les gros appareils refrigerants ne sont plus ramasses avec les encombrants. La Ville les ramasse "
          "gratuitement a domicile sur demande (en ligne, par courriel a travauxpublics@sainte-therese.ca ou "
          "au 450 434-1440, poste 2100), dans un delai de 3 jours ouvrables. Vous pouvez aussi les apporter "
          "a l'ecocentre.",
          "Large refrigerating appliances are no longer picked up with bulky items. The city collects them "
          "from your home for free on request (online, by email to travauxpublics@sainte-therese.ca or at "
          "450 434-1440, ext. 2100), within 3 working days. You can also take them to the ecocentre.",
          "大型制冷电器不再随大件垃圾收集。市政府可应申请免费上门收取（网上申请、发邮件至 "
          "travauxpublics@sainte-therese.ca 或致电 450 434-1440 转 2100），3 个工作日内完成。也可自行送到生态中心。"),
         (["refrigerateur", "congelateur", "climatiseur portatif"], ["fridge", "freezer", "portable air conditioner"],
          ["冰箱", "冰柜", "移动空调"]),
         (["refrigerateur", "frigo", "congelateur", "climatiseur", "thermopompe"],
          ["fridge", "refrigerator", "freezer", "air conditioner", "heat pump"], ["冰箱", "冰柜", "空调"])),

    item('christmas-trees', 'special', PAGE,
         ("Sapins de Noel", "Christmas trees", "圣诞树"),
         ("Les sapins naturels sont ramasses le 12 janvier 2027, sur inscription seulement: en ligne (option "
          "collecte de sapin) ou au 450 434-1440, poste 2100, au plus tard le 10 janvier a 7 h. Sans aucune "
          "decoration, en bordure de rue, la partie coupee face a la rue.",
          "Natural trees are collected on 12 January 2027, by registration only: online (Christmas tree "
          "option) or at 450 434-1440, ext. 2100, by 10 January at 7 a.m. With every decoration removed, at "
          "the curb, cut end facing the street.",
          "天然圣诞树于 2027 年 1 月 12 日收集，仅限登记：网上申请（选择圣诞树收集）或致电 450 434-1440 转 "
          "2100，截止 1 月 10 日早上 7 点。请去掉所有装饰，放在路边，切口朝向街道。"),
         (["sapin naturel"], ["real Christmas tree"], ["真圣诞树"]),
         (["sapin", "noel"], ["christmas tree", "tree", "christmas"], ["圣诞树"]),
         availability=("12 janvier 2027, sur inscription au plus tard le 10 janvier a 7 h.",
                       "12 January 2027, register by 10 January at 7 a.m.",
                       "2027 年 1 月 12 日，须在 1 月 10 日早上 7 点前登记。")),

    item('cedar-trimmings', 'special', PAGE,
         ("Retailles de cedre", "Cedar trimmings", "雪松修剪枝叶"),
         ("Du 1er mai au 31 octobre, Arbressence ramasse gratuitement les retailles de cedre: reservez en "
          "ligne sur arbressence.ca. Retailles d'au plus un pouce de diametre et trois pieds de long; pas de "
          "branches.",
          "From 1 May to 31 October, Arbressence collects cedar trimmings for free: book online at "
          "arbressence.ca. Trimmings at most one inch across and three feet long; no branches.",
          "5 月 1 日至 10 月 31 日，Arbressence 免费收集雪松修剪枝叶：在 arbressence.ca 网上预约。"
          "直径不超过 1 英寸、长度不超过 3 英尺；不收树枝。"),
         (["retailles de haie de cedre"], ["cedar hedge trimmings"], ["雪松绿篱修剪物"]),
         (["cedre", "haie", "retailles"], ["cedar", "hedge", "trimmings"], ["雪松", "绿篱"]),
         availability=("Du 1er mai au 31 octobre, ramassage dans les 48 heures.",
                       "1 May to 31 October, collected within 48 hours.",
                       "5 月 1 日至 10 月 31 日，48 小时内收集。")),
]


def q(s):
    return "'" + s.replace("'", "''") + "'" if s is not None else 'null'


def render():
    keys = [i['key'] for i in ITEMS]
    assert len(keys) == len(set(keys)), 'item_key must be unique within the city'
    for i in ITEMS:
        for lang_words in i['keywords']:
            for word in lang_words:
                # Chinese is searched through the ngram index, which has no
                # minimum; for fr/en a two-letter keyword is never indexed.
                if word.isascii():
                    assert len(word) >= 3, (i['key'], word)

    out = [HEADER]
    for i in ITEMS:
        out.append("insert into sorting_item (municipality_id, item_key, destination_type, bin_color, source_url) "
                   f"values (@st, {q(i['key'])}, {q(i['dest'])}, {q(BIN[i['dest']])}, {q(i['src'])});")
        out.append("set @id = last_insert_id();")
        loc = i['location'] or (None, None, None)
        av = i['availability'] or (None, None, None)
        rows = ", ".join(f"(@id, {q(lang)}, {q(n)}, {q(ins)}, {q(lo)}, {q(a)})"
                         for lang, n, ins, lo, a in zip(('fr', 'en', 'zh'), i['names'], i['instr'], loc, av))
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
    out.append(FOOTER)
    return "\n".join(out)


HEADER = f"""-- --------------------------------------------------------------------------
-- Sainte-Therese's sorting guide, from the city's own regulation.
--
-- GENERATED by docs/verification/generate_v23_sainte_therese_guide.py; edit
-- that, not this. {len(ITEMS)} items over four tables in three languages is
-- exactly the transcription where a silent mistake hides.
--
-- Source: Reglement 1373 N.S. relatif a la gestion des matieres residuelles,
-- adopted 4 May 2026, in force 5 May 2026. Its article 14 makes annexes A to
-- E the binding lists of what each bin, the bulky pickup and the ecocentre
-- accept. The city's collections and ecocentre pages fill in the how and
-- when. All three are archived under
-- data/municipal-sources/sainte-therese/2026-11-17/ with their sha256.
--
-- The city's own "Matieres acceptees" buttons point to RECYC-QUEBEC's "Ca va
-- ou?" app rather than a list on its site; the regulation is used instead,
-- because it is the city's own document and it is the law.
--
-- Where this city's answer differs from Blainville's for the same item_key -
-- the reason the guides are per city at all:
--   light-bulb              ecocentre (hazardous)   Blainville: black bin
--   broken-glass-or-mirror  black bin               Blainville: bulky pickup
--   branches                brown bin (small)       Blainville: special pickup
--   appliance-and-bbq       not reused: the bulky pickup takes non-
--                           refrigerating appliances, the ecocentre takes
--                           barbecues as metal, and fridges have their own
--                           pickup, so it is three items here, not one.
-- And items Blainville's guide has no row for, where a resident's guess is
-- likely wrong: pet litter goes in the BROWN bin; foam food containers,
-- plastic bags and coffee capsules go in the BLUE bin; photos and CDs go in
-- the BLACK bin.
--
-- Two places the city's own documents disagree, recorded rather than
-- resolved by guessing:
--   * Branches. The regulation (annexe C) allows brown-bin branches up to
--     1 cm across and 60 cm long; the collections page says branches under
--     4 cm go in the brown bin. The entry states the regulation's limit,
--     because it is the binding text, and names the page's branch pickup
--     (4 to 15 cm) and the ecocentre for the rest.
--   * Appliances. The regulation defines a bulky item as one the ecocentre
--     does not accept, and annexe E has appliances at the ecocentre; the
--     collections page lists non-refrigerating appliances among what the
--     bulky pickup takes. The entry follows the page, which is the more
--     specific and the more recent statement of practice, and the ecocentre
--     takes them too.
--
-- Not loaded: books (annexe B's line about them is garbled in the published
-- PDF), and RECYC-QUEBEC's own lists.
--
-- Also, from the same collections page, two calendar facts V22 did not have:
--   * The Christmas tree pickup is on 12 January 2027, registration only.
--   * Bulky items go out on "le 1er lundi du mois pour l'ensemble du
--     territoire". V22's comment said the page states no rule for them; that
--     was wrong. The first Mondays through November 2027, the same horizon
--     the organics rules stop at, are loaded below as rows, because a
--     monthly pattern is not a weekly rule.
-- --------------------------------------------------------------------------

set @st = (select id from municipality where slug = 'sainte-therese');
"""

FIRST_MONDAYS_2027 = ['2027-01-04', '2027-02-01', '2027-03-01', '2027-04-05', '2027-05-03', '2027-06-07',
                      '2027-07-05', '2027-08-02', '2027-09-06', '2027-10-04', '2027-11-01']


def footer():
    bulky = ",\n".join(
        f"(@st, '{d}', 'all', 'bulky', 'none', 'Collecte des encombrants, pour toutes les zones.', "
        f"'Bulky item collection, in every zone.', '大件垃圾收集，所有区相同。', {q(PAGE)})"
        for d in FIRST_MONDAYS_2027)
    return f"""-- --------------------------------------------------------------------------
-- Calendar facts from the collections page.
-- --------------------------------------------------------------------------
insert into collection_event
(municipality_id, collection_date, sector, collection_type, bin_color, note_fr, note_en, note_zh, source_url)
values
(@st, '2027-01-12', 'all', 'special', 'none',
 'Collecte des sapins de Noel, sur inscription seulement, au plus tard le 10 janvier a 7 h.',
 'Christmas tree collection, by registration only, by 10 January at 7 a.m.',
 '圣诞树收集，仅限登记，截止 1 月 10 日早上 7 点。',
 {q(PAGE)}),
{bulky};

-- Last, and only for what was loaded.
update municipality set guide_verified = true where id = @st;
"""


FOOTER = footer()

if __name__ == '__main__':
    import datetime
    for d in FIRST_MONDAYS_2027:
        day = datetime.date.fromisoformat(d)
        assert day.weekday() == 0 and day.day <= 7, d
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    target = os.path.join(root, 'backend/src/main/resources/db/migration/V23__sainte_therese_sorting_guide.sql')
    io.open(target, 'w', encoding='utf-8', newline='\n').write(pg_sql.convert(render()))
    print(f'wrote {target}: {len(ITEMS)} items')
