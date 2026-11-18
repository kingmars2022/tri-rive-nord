# -*- coding: utf-8 -*-
"""Emits V28: Boisbriand's sorting guide.

Sources (archived under data/municipal-sources/boisbriand/2026-11-17/ and
data/municipal-sources/sainte-therese/2026-11-17/):

  PAGE  The city's 2026 "Collectes des matieres residuelles" page, saved by
        hand (the site refuses automated clients). It states the rules: the
        brown bin's three questions, no plastic bags of any kind, branch sizes,
        the 2026 green-waste, branch, cedar and excess-waste dates, and that
        the ecocentre is Sainte-Therese's.
  CAL   Pages 3-5 of the city's "Calendrier des collectes 2025", the edition
        V25 already uses at the owner's direction: the blue-bin examples and
        exceptions, the brown-bin item list, the black bin's definition and the
        refused-items list. The 2026 page agrees with it wherever both speak.
  ECO   Sainte-Therese's ecocentre page, which Boisbriand's page links to as
        its own ("Situe a Sainte-Therese, l'ecocentre ... mis a la disposition
        des citoyens de Boisbriand").

Every card cites PAGE, the city's current page a resident can check, except
ecocentre cards, which cite ECO. The 2025 PDF's own address was not recorded
when it was downloaded, so it cannot be cited; which facts came from it is
listed in V28's header.

Rules for what is NOT here: where the city says only "not in the blue bin"
and never where it does go (syringes), there is no card. Where a destination
follows from the city's own definition rather than being printed against the
item, the card says so.

    python3 docs/verification/generate_v28_boisbriand_guide.py
"""
import io
import os

PAGE = 'https://www.ville.boisbriand.qc.ca/services/citoyens/collectes-des-matieres-residuelles'
ECO = 'https://www.sainte-therese.ca/services/services-aux-citoyens/ecocentre'

BIN = {'organic': 'brown', 'recycling': 'blue', 'garbage': 'black',
       'ecocentre': 'none', 'bulky': 'none', 'special': 'none'}

ECO_LOCATION = ('Ecocentre, 105 rue Blanchard, Sainte-Therese',
                'Ecocentre, 105 rue Blanchard, Sainte-Therese',
                '生态中心，105 rue Blanchard, Sainte-Therese')
ECO_HOURS = ('Vendredi 12 h a 19 h; samedi et dimanche 9 h a 18 h. Preuve de residence de Boisbriand exigee. '
             'Ferme du 21 decembre 2026 au 7 janvier 2027.',
             'Friday noon to 7 p.m.; Saturday and Sunday 9 a.m. to 6 p.m. Proof of Boisbriand residence required. '
             'Closed from 21 December 2026 to 7 January 2027.',
             '周五 12:00-19:00；周六、周日 9:00-18:00。需出示 Boisbriand 居住证明。'
             '2026 年 12 月 21 日至 2027 年 1 月 7 日关闭。')


def item(key, dest, src, names, instr, examples, keywords, location=None, availability=None):
    return dict(key=key, dest=dest, src=src, names=names, instr=instr, examples=examples,
                keywords=keywords, location=location, availability=availability)


def eco(key, names, instr, examples, keywords):
    return item(key, 'ecocentre', ECO, names, instr, examples, keywords, ECO_LOCATION, ECO_HOURS)


ITEMS = [
    # ------------------------------------------------------------ bac brun
    # The rule (PAGE): "Ca se mange; c'est en papier ou du carton (propre ou
    # souille, non cire); c'est un residu de jardin (sauf les branches)".
    # The items (CAL page 4).
    item('meat-and-bones', 'organic', PAGE,
         ("Viandes, os et graisses", "Meat, bones and fat", "肉类、骨头和油脂"),
         ("Les viandes, les os et les graisses vont au bac brun. Les graisses en petites quantites, absorbees "
          "dans du papier ou du carton.",
          "Meat, bones and fat go in the brown bin. Fat in small amounts, soaked up in paper or cardboard.",
          "肉类、骨头和油脂放入棕桶。油脂请少量，并用纸或纸板吸附。"),
         (["viande", "os de poulet", "gras"], ["meat", "chicken bones", "grease"], ["肉", "骨头", "油脂"]),
         (["viande", "poulet", "ossements", "gras", "graisse"], ["meat", "chicken", "bones", "fat", "grease"],
          ["肉", "鸡肉", "骨头", "油脂"])),
    item('fish-and-seafood', 'organic', PAGE,
         ("Poissons et fruits de mer", "Fish and seafood", "鱼类和海鲜"),
         ("Les poissons et les fruits de mer vont au bac brun.",
          "Fish and seafood go in the brown bin.",
          "鱼和海鲜放入棕桶。"),
         (["poisson", "crevettes", "moules"], ["fish", "shrimp", "mussels"], ["鱼", "虾", "贝类"]),
         (["poisson", "fruits de mer", "crevette"], ["fish", "seafood", "shrimp"], ["鱼", "海鲜", "虾"])),
    item('eggs-and-eggshells', 'organic', PAGE,
         ("Oeufs et coquilles", "Eggs and eggshells", "鸡蛋和蛋壳"),
         ("Les oeufs et leurs coquilles vont au bac brun.",
          "Eggs and their shells go in the brown bin.",
          "鸡蛋和蛋壳放入棕桶。"),
         (["coquilles d'oeufs"], ["eggshells"], ["蛋壳"]),
         (["oeuf", "oeufs", "coquille", "coquilles"], ["egg", "eggs", "eggshell", "eggshells"], ["鸡蛋", "蛋壳"])),
    item('solid-dairy-products', 'organic', PAGE,
         ("Produits laitiers et fromages", "Dairy products and cheese", "乳制品和奶酪"),
         ("Les produits laitiers et les fromages vont au bac brun.",
          "Dairy products and cheese go in the brown bin.",
          "乳制品和奶酪放入棕桶。"),
         (["fromage", "yogourt", "beurre"], ["cheese", "yogurt", "butter"], ["奶酪", "酸奶", "黄油"]),
         (["fromage", "laitier", "yogourt", "beurre"], ["cheese", "dairy", "yogurt", "butter"], ["奶酪", "乳制品", "酸奶"])),
    item('fruit-and-vegetable-scraps', 'organic', PAGE,
         ("Fruits et legumes", "Fruit and vegetables", "水果和蔬菜"),
         ("Les residus de fruits et de legumes vont au bac brun.",
          "Fruit and vegetable scraps go in the brown bin.",
          "水果和蔬菜残渣放入棕桶。"),
         (["pelures", "trognon de pomme"], ["peels", "apple core"], ["果皮", "果核"]),
         (["fruit", "legume", "pelure", "epluchure"], ["fruit", "vegetable", "peel", "scraps"], ["水果", "蔬菜", "果皮"])),
    item('nuts-and-nutshells', 'organic', PAGE,
         ("Noix et ecales", "Nuts and nutshells", "坚果和坚果壳"),
         ("Les noix et leurs ecales vont au bac brun.",
          "Nuts and their shells go in the brown bin.",
          "坚果和坚果壳放入棕桶。"),
         (["ecales de noix", "coquilles d'arachides"], ["nutshells", "peanut shells"], ["坚果壳", "花生壳"]),
         (["noix", "ecale", "arachide"], ["nuts", "nutshell", "peanut"], ["坚果", "花生"])),
    item('tea-and-coffee-grounds', 'organic', PAGE,
         ("The, tisane et cafe", "Tea, herbal tea and coffee", "茶叶、花草茶和咖啡渣"),
         ("Les residus de the, de tisane et de cafe vont au bac brun, avec les filtres a cafe en papier.",
          "Tea, herbal tea and coffee grounds go in the brown bin, with paper coffee filters.",
          "茶叶、花草茶和咖啡渣放入棕桶，纸质咖啡滤纸也一样。"),
         (["marc de cafe", "sachet de the"], ["coffee grounds", "tea bag"], ["咖啡渣", "茶包"]),
         (["cafe", "tisane", "marc", "filtre"], ["coffee", "tea", "grounds", "filter"], ["咖啡", "茶叶", "滤纸"])),
    item('tissues-and-paper-napkins', 'organic', PAGE,
         ("Mouchoirs et essuie-tout", "Tissues and paper towels", "纸巾和厨房纸"),
         ("Les mouchoirs et l'essuie-tout vont au bac brun, sauf s'ils sont souilles d'un produit nettoyant "
          "nocif: alors a la poubelle.",
          "Tissues and paper towels go in the brown bin, unless soiled with a harmful cleaning product: then the "
          "garbage.",
          "纸巾和厨房纸放入棕桶；若沾有有害清洁剂，则放入垃圾桶。"),
         (["mouchoir", "essuie-tout"], ["tissue", "paper towel"], ["纸巾", "厨房纸"]),
         (["mouchoir", "essuie-tout", "serviette"], ["tissue", "paper towel", "napkin"], ["纸巾", "厨房纸", "餐巾"])),
    item('food-soiled-paper-and-cardboard', 'organic', PAGE,
         ("Papiers et cartons souilles d aliments", "Food-soiled paper and cardboard", "沾有食物的纸和纸板"),
         ("Les papiers et cartons souilles par la nourriture vont au bac brun: boites de pizza, sacs de frites, "
          "papiers de muffins, papier parchemin. S'ils sont cires, plastifies ou lamines, c'est la poubelle.",
          "Paper and cardboard soiled by food go in the brown bin: pizza boxes, fry bags, muffin papers, "
          "parchment paper. Waxed, plastic-coated or laminated ones go in the garbage.",
          "沾有食物的纸和纸板放入棕桶：披萨盒、薯条袋、松饼纸托、烘焙纸。打蜡、覆塑或覆膜的放入垃圾桶。"),
         (["boite de pizza", "sac de frites", "papier parchemin"], ["pizza box", "fry bag", "parchment paper"],
          ["披萨盒", "薯条袋", "烘焙纸"]),
         (["pizza", "frites", "muffin", "parchemin"], ["pizza", "fries", "muffin", "parchment"],
          ["披萨盒", "烘焙纸", "脏纸板"])),
    item('waxed-or-laminated-paper', 'garbage', PAGE,
         ("Papier cire, plastifie ou lamine", "Waxed, plastic-coated or laminated paper", "打蜡、覆塑或覆膜的纸"),
         ("Les papiers et cartons cires, plastifies ou lamines vont a la poubelle, comme ceux souilles d'un "
          "produit nettoyant nocif.",
          "Waxed, plastic-coated or laminated paper and cardboard go in the garbage, as do any soiled with a "
          "harmful cleaning product.",
          "打蜡、覆塑或覆膜的纸和纸板放入垃圾桶，沾有有害清洁剂的纸也一样。"),
         (["papier cire", "carton plastifie"], ["wax paper", "coated cardboard"], ["蜡纸", "覆膜纸板"]),
         (["cire", "plastifie", "lamine"], ["waxed", "wax paper", "laminated", "coated"], ["蜡纸", "覆膜"])),
    item('yard-waste', 'organic', PAGE,
         ("Residus verts", "Yard waste", "园林垃圾"),
         ("Plantes, feuilles, copeaux non traites, terre et terreau en petites quantites vont au bac brun. En "
          "avril, mai, octobre et novembre, les surplus sont ramasses a cote du bac: contenant marque d'un V, "
          "sac de papier ou boite de carton, 23 kg au plus. Jamais de sac de plastique.",
          "Plants, leaves, untreated wood chips, and soil and potting soil in small amounts go in the brown bin. "
          "In April, May, October and November surplus is collected beside the bin: a container marked V, a "
          "paper bag or a cardboard box, 23 kg at most. Never plastic bags.",
          "植物、落叶、未处理木屑以及少量泥土和盆栽土放入棕桶。4、5、10、11 月，桶旁多出的部分也会收走："
          "请用标有 V 的容器、纸袋或纸箱，每件不超过 23 公斤。不收塑料袋。"),
         (["feuilles mortes", "gazon", "terreau"], ["leaves", "grass clippings", "potting soil"], ["落叶", "草屑", "盆栽土"]),
         # The card's own name words as keywords: "yard waste" otherwise
         # matched "dog waste" on the litter card, and 园林垃圾 the garbage
         # card through 垃圾 - both caught by the own-name test.
         (["feuilles", "gazon", "plantes", "terreau", "copeaux", "residus verts"],
          ["yard waste", "yard", "leaves", "grass", "plants", "soil", "chips"],
          ["园林垃圾", "园林", "落叶", "草", "植物", "泥土"])),
    item('invasive-plants', 'garbage', PAGE,
         ("Plantes envahissantes", "Invasive plants", "入侵植物"),
         ("Les plantes envahissantes ne vont pas au bac brun: jetez-les aux ordures.",
          "Invasive plants do not go in the brown bin: put them in the garbage.",
          "入侵植物不要放入棕桶，请放入垃圾桶。"),
         (["renouee du Japon", "nerprun"], ["Japanese knotweed", "buckthorn"], ["日本虎杖", "鼠李"]),
         (["envahissante", "renouee", "nerprun", "berce"], ["invasive", "knotweed", "buckthorn", "hogweed"],
          ["入侵植物"])),
    item('branches', 'organic', PAGE,
         ("Branches", "Branches", "树枝"),
         ("Les branches de 30 cm et moins et de 4 cm de diametre ou moins vont au bac brun; coupez les longues "
          "branches minces. En 2026, la Ville a ramasse les branches plus grosses du 28 avril au 29 octobre, sur "
          "inscription (450 435-1954). Hors de cette periode, apportez-les a l'ecocentre (frais applicables).",
          "Branches 30 cm or shorter and 4 cm across or less go in the brown bin; cut long thin ones. In 2026 the "
          "city collected larger branches from 28 April to 29 October, by registration (450 435-1954). Outside "
          "that period, take them to the ecocentre (fees apply).",
          "长度 30 厘米以内、直径 4 厘米以内的树枝放入棕桶；细长树枝请剪短。2026 年市政府于 4 月 28 日至 10 月 29 日"
          "登记收集较粗的树枝（450 435-1954）。其他时间请送到生态中心（收费）。"),
         (["petites branches"], ["small branches"], ["小树枝"]),
         (["branche", "elagage"], ["branch", "branches", "pruning"], ["树枝", "修剪"])),
    item('hair-and-feathers', 'organic', PAGE,
         ("Cheveux, poils et plumes", "Hair, fur and feathers", "头发、毛发和羽毛"),
         ("Les cheveux, les poils et les plumes vont au bac brun.",
          "Hair, fur and feathers go in the brown bin.",
          "头发、动物毛发和羽毛放入棕桶。"),
         (["cheveux", "poils de chien"], ["hair", "dog hair"], ["头发", "狗毛"]),
         (["cheveux", "poils", "plumes"], ["hair", "fur", "feathers"], ["头发", "毛发", "羽毛"])),
    item('toothpicks-and-wooden-sticks', 'organic', PAGE,
         ("Cure-dents et batonnets de bois", "Toothpicks and wooden sticks", "牙签和木棒"),
         ("Les cure-dents et les batonnets de bois vont au bac brun.",
          "Toothpicks and wooden sticks go in the brown bin.",
          "牙签和木棒放入棕桶。"),
         (["cure-dents", "baton de popsicle"], ["toothpicks", "popsicle stick"], ["牙签", "冰棍棒"]),
         (["cure-dent", "batonnet", "bois"], ["toothpick", "stick", "wooden"], ["牙签", "木棒"])),
    item('pet-litter', 'organic', PAGE,
         ("Litiere et excrements d'animaux", "Pet litter and animal waste", "猫砂和宠物粪便"),
         ("A Boisbriand, la litiere, les excrements d'animaux domestiques et le fumier de poule vont au bac "
          "brun.",
          "In Boisbriand, pet litter, pet waste and chicken manure go in the brown bin.",
          "在 Boisbriand，猫砂、宠物粪便和鸡粪放入棕桶。"),
         (["litiere a chat", "crottes de chien", "fumier de poule"], ["cat litter", "dog poop", "chicken manure"],
          ["猫砂", "狗屎", "鸡粪"]),
         (["litiere", "excrements", "crottes", "fumier"], ["litter", "poop", "dog waste", "manure"],
          ["猫砂", "粪便", "狗屎"])),
    item('wood-ashes', 'organic', PAGE,
         ("Cendres refroidies", "Cooled ashes", "冷却的灰烬"),
         ("Les cendres vont au bac brun si elles sont refroidies depuis plus de 72 heures.",
          "Ashes go in the brown bin once they have been cold for more than 72 hours.",
          "灰烬冷却超过 72 小时后可放入棕桶。"),
         (["cendres de foyer", "cendres de BBQ"], ["fireplace ashes", "BBQ ashes"], ["壁炉灰", "烧烤灰"]),
         (["cendres", "foyer", "poele"], ["ashes", "ash", "fireplace"], ["灰烬", "壁炉灰"])),
    item('compostable-or-biodegradable-dishes-and-bags', 'garbage', PAGE,
         ("Sacs de plastique, meme compostables", "Plastic bags, even compostable", "塑料袋（包括可堆肥袋）"),
         ("Aucun sac de plastique, meme transparent, biodegradable ou compostable, n'est accepte dans le bac "
          "brun ni dans les surplus de residus verts; les plastiques degradables ne vont pas non plus au bac de "
          "recuperation. Ils vont donc aux dechets. Utilisez un sac de papier.",
          "No plastic bag, even clear, biodegradable or compostable, is accepted in the brown bin or with green-"
          "waste surplus; degradable plastics do not go in the recycling bin either. So they go in the garbage. "
          "Use a paper bag.",
          "棕桶和园林垃圾都不收任何塑料袋，包括透明、可降解或可堆肥的；可降解塑料也不能放入回收桶。"
          "因此请放入垃圾桶，并改用纸袋。"),
         (["sac compostable", "sac biodegradable"], ["compostable bag", "biodegradable bag"], ["可堆肥袋", "可降解袋"]),
         (["compostable", "biodegradable", "sac plastique"], ["compostable", "biodegradable", "bioplastic"],
          ["可堆肥", "可降解", "塑料袋"])),

    # ------------------------------------------------------------ bac bleu
    # CAL page 3: "Contenants, emballages, imprimes. C'est tout."
    item('containers-and-packaging', 'recycling', PAGE,
         ("Contenants, emballages et imprimes", "Containers, packaging and printed paper", "容器、包装和印刷品"),
         ("Le bac de recuperation prend les contenants, les emballages et les imprimes. C'est tout. Videz et "
          "rincez legerement; retirez les couvercles des pots, sauf les petits bouchons; separez les matieres "
          "d'un emballage.",
          "The recycling bin takes containers, packaging and printed paper. That is all. Empty and lightly "
          "rinse; take lids off jars, except small caps; separate the materials of a package.",
          "回收桶只收容器、包装和印刷品。请倒空并简单冲洗；取下罐子的盖子（小瓶盖除外）；把包装的不同材料分开。"),
         (["bouteille", "contenant de produit d'entretien", "pot de yogourt"],
          ["bottle", "cleaning product container", "yogurt tub"], ["瓶子", "清洁剂瓶", "酸奶杯"]),
         (["contenant", "emballage", "bouteille", "plastique", "conserve"],
          ["container", "packaging", "bottle", "plastic", "can"], ["容器", "包装", "瓶子", "塑料"])),
    item('printed-paper', 'recycling', PAGE,
         ("Journaux, circulaires et papier", "Newspapers, flyers and paper", "报纸、传单和纸张"),
         ("Les journaux, circulaires, enveloppes, feuilles et cahiers vont au bac de recuperation.",
          "Newspapers, flyers, envelopes, sheets of paper and notebooks go in the recycling bin.",
          "报纸、传单、信封、纸张和笔记本放入回收桶。"),
         (["journal", "circulaire", "enveloppe"], ["newspaper", "flyer", "envelope"], ["报纸", "传单", "信封"]),
         (["journal", "circulaire", "enveloppe", "cahier", "papier"], ["newspaper", "flyer", "envelope", "paper"],
          ["报纸", "传单", "信封", "纸"])),
    item('cardboard-boxes', 'recycling', PAGE,
         ("Boites de carton", "Cardboard boxes", "纸箱"),
         ("Defaites et pliees, les boites de carton vont au bac de recuperation. Les surplus de carton a cote du "
          "bac ne sont ramasses que lors de deux collectes speciales par annee (en 2026: 13 au 16 janvier, 7 au "
          "10 juillet). Les tres grandes boites vont gratuitement a l'ecocentre.",
          "Broken down and flattened, cardboard boxes go in the recycling bin. Extra cardboard beside the bin is "
          "only taken at two special collections a year (in 2026: 13-16 January, 7-10 July). Very large boxes go "
          "to the ecocentre, free.",
          "纸箱拆开压平后放入回收桶。桶旁多出的纸板每年只在两次特别收集时收走（2026 年：1 月 13-16 日、"
          "7 月 7-10 日）。很大的纸箱可免费送到生态中心。"),
         (["boite de livraison", "boite de cereales"], ["shipping box", "cereal box"], ["快递纸箱", "麦片盒"]),
         (["carton", "boite"], ["cardboard", "box", "boxes"], ["纸箱", "纸盒", "纸板"])),
    item('aluminium-cans-and-foil', 'recycling', PAGE,
         ("Assiettes et papier d'aluminium", "Aluminium plates and foil", "铝盘和铝箔"),
         ("Les assiettes et le papier d'aluminium vont au bac de recuperation; faites une boulette du papier, "
          "meme legerement souille.",
          "Aluminium plates and foil go in the recycling bin; roll the foil into a ball, even if slightly soiled.",
          "铝盘和铝箔放入回收桶；铝箔即使略脏，也请揉成团。"),
         (["papier d'aluminium", "assiette d'aluminium"], ["tin foil", "foil plate"], ["锡纸", "铝盘"]),
         (["aluminium", "papier aluminium"], ["aluminium", "aluminum", "foil"], ["铝箔", "锡纸"])),
    item('take-out-containers', 'recycling', PAGE,
         ("Emballages de mets a emporter", "Take-out containers", "外卖餐盒"),
         ("Les emballages de mets a emporter et de mets prepares vont au bac de recuperation.",
          "Take-out and ready-meal containers go in the recycling bin.",
          "外卖和预制餐的包装盒放入回收桶。"),
         (["contenant pour emporter", "barquette de mets prepares"], ["take-out box", "ready-meal tray"],
          ["外卖盒", "预制餐盒"]),
         (["emporter", "mets prepares"], ["take-out", "takeout", "ready meal"], ["外卖盒", "餐盒"])),

    # ------------------------------------------- not the blue bin, by name
    eco('aerosol-cans',
        ("Aerosols", "Aerosol cans", "气雾罐"),
        ("Les aerosols ne vont pas au bac de recuperation: apportez-les a l'ecocentre.",
         "Aerosol cans do not go in the recycling bin: take them to the ecocentre.",
         "气雾罐不能放入回收桶，请送到生态中心。"),
        (["bombe aerosol", "fixatif"], ["spray can", "hairspray"], ["喷雾罐", "发胶"]),
        (["aerosol", "bombe", "vaporisateur"], ["aerosol", "spray", "spray can"], ["喷雾", "气雾罐"])),
    eco('styrofoam-protective-packaging',
        ("Styromousse de protection", "Protective styrofoam packaging", "保护用泡沫包装"),
        ("Les emballages de protection en polystyrene ne vont pas au bac de recuperation: apportez-les a "
         "l'ecocentre, propres et tries.",
         "Protective polystyrene packaging does not go in the recycling bin: take it to the ecocentre, clean and "
         "sorted.",
         "保护用聚苯乙烯泡沫不能放入回收桶，请清洁分类后送到生态中心。"),
        (["bloc de styromousse", "styromousse de television"], ["foam packing block", "TV foam"], ["泡沫块", "电视泡沫"]),
        (["styromousse", "polystyrene", "protection"], ["styrofoam", "polystyrene", "packing foam"], ["泡沫", "聚苯乙烯"])),
    eco('batteries',
        ("Piles et batteries", "Batteries", "电池"),
        ("Les piles ne vont pas au bac de recuperation. L'ecocentre les accepte; regroupez les piles au lithium "
         "dans un sac a part.",
         "Batteries do not go in the recycling bin. The ecocentre takes them; bag lithium batteries separately.",
         "电池不能放入回收桶。生态中心接收电池；锂电池请单独装袋。"),
        (["piles AA", "pile au lithium"], ["AA batteries", "lithium battery"], ["5 号电池", "锂电池"]),
        (["pile", "piles", "batterie", "lithium"], ["battery", "batteries", "lithium"], ["电池", "废电池", "锂电池"])),
    eco('pots-and-pans',
        ("Casseroles et poeles", "Pots and pans", "锅具"),
        ("Une casserole n'est ni un contenant ni un emballage: elle ne va pas au bac de recuperation. "
         "L'ecocentre accepte le metal.",
         "A pot is neither a container nor packaging: it does not go in the recycling bin. The ecocentre takes "
         "metal.",
         "锅具既不是容器也不是包装，不能放入回收桶。生态中心接收金属。"),
        (["casserole", "poele a frire"], ["pot", "frying pan"], ["锅", "平底锅"]),
        (["casserole", "poele", "chaudron"], ["pot", "pan", "frying pan"], ["锅", "平底锅"])),
    item('broken-toy', 'garbage', PAGE,
         ("Jouets, boyaux et chaises de jardin", "Toys, garden hoses and garden chairs", "玩具、水管和花园椅"),
         ("Jouets, boyaux d'arrosage et chaises de jardin ne vont pas au bac de recuperation. Donnez-les s'ils "
          "servent encore; sinon, ce sont des dechets: le calendrier de la Ville definit le bac de dechets comme "
          "ce qui n'est ni recyclable, ni compostable, ni valorisable, ni dangereux.",
          "Toys, garden hoses and garden chairs do not go in the recycling bin. Give them away if they still "
          "work; otherwise they are garbage: the city's calendar defines the garbage bin as what is not "
          "recyclable, compostable, recoverable or hazardous.",
          "玩具、浇花水管和花园椅不能放入回收桶。还能用就送人；否则属于垃圾：市政府日历将垃圾桶定义为"
          "不可回收、不可堆肥、不可再利用、无危险的物品。"),
         (["jouet brise", "boyau d'arrosage", "chaise de patio"], ["broken toy", "garden hose", "patio chair"],
          ["坏玩具", "水管", "花园椅"]),
         (["jouet", "boyau", "chaise", "arrosage"], ["toy", "hose", "garden chair", "patio chair"],
          ["玩具", "水管", "椅子"])),
    item('clothing-and-textiles', 'special', PAGE,
         ("Vetements et tissus", "Clothing and textiles", "衣物和纺织品"),
         ("Les vetements et les tissus ne vont pas au bac de recuperation. Donnez-les: les Fripes du Relais, "
          "305 chemin de la Grande-Cote, les acceptent pendant les heures d'ouverture, ou dans la cloche de dons "
          "a droite de l'entree du IGA du Faubourg Boisbriand.",
          "Clothing and textiles do not go in the recycling bin. Donate them: Fripes du Relais, 305 chemin de la "
          "Grande-Cote, takes them during opening hours, or use the donation bin to the right of the IGA "
          "entrance at Faubourg Boisbriand.",
          "衣物和纺织品不能放入回收桶。请捐赠：Fripes du Relais（305 chemin de la Grande-Cote）营业时间接收，"
          "或投入 Faubourg Boisbriand 的 IGA 入口右侧的捐赠箱。"),
         (["vetements usages", "draps"], ["used clothes", "sheets"], ["旧衣服", "床单"]),
         (["vetement", "tissu", "linge", "souliers"], ["clothing", "clothes", "textile", "shoes"],
          ["衣服", "旧衣服", "纺织品"])),

    # ------------------------------------------------------------ bac noir
    item('household-waste', 'garbage', PAGE,
         ("Dechets", "Garbage", "生活垃圾"),
         ("Le bac de dechets est pour ce qui n'est ni recyclable, ni compostable, ni valorisable, ni dangereux, "
          "et qui est en fin de vie. Il est ramasse aux deux semaines; 250 lb au plus.",
          "The garbage bin is for what is not recyclable, compostable, recoverable or hazardous, and has reached "
          "the end of its life. It is collected every two weeks; 250 lb at most.",
          "垃圾桶用于不可回收、不可堆肥、不可再利用、无危险且已无用的物品。每两周收一次，重量不超过 250 磅。"),
         (["dechets", "poubelle"], ["trash", "garbage"], ["垃圾"]),
         (["dechets", "poubelle", "ordures"], ["garbage", "trash", "waste"], ["垃圾", "生活垃圾"])),

    # ------------------------------------------------------- excess waste
    item('bulky-items', 'bulky', PAGE,
         ("Dechets excedentaires et meubles", "Excess waste and furniture", "超量垃圾和家具"),
         ("Une fois par mois, le jour habituel de votre collecte, les dechets qui n'entrent pas dans le bac sont "
          "ramasses: meubles, petits electromenagers sans halocarbures, materiaux de construction sans clous, "
          "ensaches ou attaches, 23 kg par paquet, 5 m3 par adresse. En 2026, la derniere est du 15 au 18 "
          "decembre. Refuses: gros electromenagers et appareils refrigerants, electroniques, pneus, residus "
          "dangereux, troncs et souches. Un meuble encore bon: le Grenier populaire le ramasse a domicile.",
          "Once a month, on your usual collection day, waste that does not fit in the bin is collected: "
          "furniture, small appliances without halocarbons, nail-free building materials, bagged or tied, 23 kg "
          "a bundle, 5 m3 per address. In 2026 the last one is 15-18 December. Refused: large and refrigerating "
          "appliances, electronics, tires, hazardous waste, trunks and stumps. Furniture still in good shape: "
          "Grenier populaire picks it up from your home.",
          "每月一次，在您平常的收运日收取放不进垃圾桶的物品：家具、不含卤烃的小家电、无钉建材，需装袋或捆扎，"
          "每捆 23 公斤以内，每个地址最多 5 立方米。2026 年最后一次是 12 月 15-18 日。不收：大型家电和制冷电器、"
          "电子产品、轮胎、危险废物、树干和树桩。还能用的家具可由 Grenier populaire 上门收取。"),
         (["divan", "matelas", "table"], ["couch", "mattress", "table"], ["沙发", "床垫", "桌子"]),
         (["meuble", "matelas", "divan", "excedentaire", "encombrant"],
          ["furniture", "mattress", "couch", "bulky", "excess"], ["家具", "床垫", "沙发", "大件垃圾"]),
         availability=("Une fois par mois, le jour habituel de votre collecte. En bordure de la chaussee des 18 h la "
                       "veille ou avant 7 h le jour meme.",
                       "Once a month, on your usual collection day. At the curb from 6 p.m. the day before or by "
                       "7 a.m. that day.",
                       "每月一次，在您平常的收运日。前一天 18 点后或当天 7 点前放到路边。")),

    # ------------------------------------------------------------ ecocentre
    eco('refrigerating-appliances',
        ("Electromenagers et appareils refrigerants", "Large and refrigerating appliances",
         "大型家电和制冷电器"),
        ("Les gros electromenagers et les appareils refrigerants ne sont pas ramasses avec les dechets "
         "excedentaires. Apportez-les a l'ecocentre, ou faites-les ramasser par le Grenier populaire, "
         "fonctionnels ou non.",
         "Large and refrigerating appliances are not taken with excess waste. Take them to the ecocentre, or have "
         "Grenier populaire pick them up, working or not.",
         "大型家电和制冷电器不随超量垃圾收集。请送到生态中心，或请 Grenier populaire 上门收取（无论能否使用）。"),
        (["refrigerateur", "laveuse", "climatiseur"], ["fridge", "washing machine", "air conditioner"],
         ["冰箱", "洗衣机", "空调"]),
        (["refrigerateur", "frigo", "congelateur", "laveuse", "secheuse", "electromenager"],
         ["fridge", "freezer", "washer", "dryer", "appliance"], ["冰箱", "冰柜", "洗衣机", "家电"])),
    eco('electronics',
        ("Appareils electroniques", "Electronics", "电子产品"),
        ("Les appareils electroniques ne sont pas ramasses; apportez-les gratuitement a l'ecocentre.",
         "Electronics are not collected; take them to the ecocentre, free.",
         "电子产品不在收运范围，可免费送到生态中心。"),
        (["televiseur", "ordinateur"], ["television", "computer"], ["电视", "电脑"]),
        (["electronique", "televiseur", "ordinateur", "cellulaire"], ["electronics", "television", "computer", "phone"],
         ["电子产品", "电视", "电脑", "手机"])),
    eco('paint',
        ("Peinture, solvants et produits dangereux", "Paint, solvents and hazardous products", "油漆、溶剂和危险品"),
        ("La peinture, les solvants et les autres residus dangereux ne vont dans aucun bac: apportez-les a "
         "l'ecocentre.",
         "Paint, solvents and other hazardous waste go in no bin: take them to the ecocentre.",
         "油漆、溶剂和其他危险废物不能放入任何垃圾桶，请送到生态中心。"),
        (["pot de peinture", "diluant"], ["paint can", "thinner"], ["油漆桶", "稀释剂"]),
        (["peinture", "solvant", "diluant", "dangereux"], ["paint", "solvent", "thinner", "hazardous"],
         ["油漆", "溶剂", "危险品"])),
    eco('tires',
        ("Pneus", "Tires", "轮胎"),
        ("Les pneus ne sont pas ramasses; apportez-les a l'ecocentre, sans jante.",
         "Tires are not collected; take them to the ecocentre, off the rim.",
         "轮胎不在收运范围，请拆下轮毂后送到生态中心。"),
        (["pneu d'auto", "pneu de velo"], ["car tire", "bike tire"], ["汽车轮胎", "自行车轮胎"]),
        (["pneu", "pneus"], ["tire", "tires", "tyre"], ["轮胎"])),
    eco('construction-debris',
        ("Materiaux de construction", "Construction materials", "建筑材料"),
        ("De petites quantites de materiaux sans clous, attaches en paquets de 23 kg ou moins, partent avec les "
         "dechets excedentaires. Avec des clous, trop lourds ou en grande quantite, ils vont a l'ecocentre; la "
         "Ville prete gratuitement une remorque pour les y apporter.",
         "Small amounts of nail-free materials, tied in bundles of 23 kg or less, go with excess waste. With "
         "nails, too heavy or in quantity, they go to the ecocentre; the city lends a trailer for free to take "
         "them there.",
         "少量无钉建材捆成每捆 23 公斤以内，可随超量垃圾收走。带钉、过重或量大的请送到生态中心；"
         "市政府免费出借拖车用于运送。"),
        (["gypse", "bois de renovation"], ["drywall", "renovation lumber"], ["石膏板", "装修木料"]),
        (["construction", "renovation", "gypse", "beton", "bardeau"], ["construction", "renovation", "drywall",
         "concrete", "shingles"], ["建筑垃圾", "装修垃圾", "石膏板"])),
    eco('stumps-and-logs',
        ("Souches, troncs et bois traite", "Stumps, logs and treated wood", "树桩、原木和处理过的木材"),
        ("Les buches, les souches, les troncs et le bois traite, peint ou teint vont a l'ecocentre (frais "
         "applicables), pas au bac brun.",
         "Logs, stumps, trunks and treated, painted or stained wood go to the ecocentre (fees apply), not the "
         "brown bin.",
         "原木、树桩、树干以及处理过、涂漆或染色的木材请送到生态中心（收费），不要放入棕桶。"),
        (["souche", "buche"], ["stump", "log"], ["树桩", "原木"]),
        (["souche", "tronc", "buche", "bois traite"], ["stump", "trunk", "log", "treated wood"], ["树桩", "树干", "原木"])),

    # ------------------------------------------------------------ special
    item('cedar-trimmings', 'special', PAGE,
         ("Retailles de cedre", "Cedar trimmings", "雪松修剪枝叶"),
         ("Du 1er mai au 31 octobre, la collecte des retailles de cedre est gratuite sur inscription en ligne "
          "(arbressence.ca). En tout temps, elles peuvent aussi aller au bac brun.",
          "From 1 May to 31 October, cedar trimmings are collected free by online registration (arbressence.ca). "
          "They can also go in the brown bin at any time.",
          "5 月 1 日至 10 月 31 日，可在网上登记（arbressence.ca）免费收集雪松修剪枝叶。任何时候也可放入棕桶。"),
         (["retailles de haie de cedre"], ["cedar hedge trimmings"], ["雪松绿篱修剪物"]),
         (["cedre", "haie", "retailles"], ["cedar", "hedge", "trimmings"], ["雪松", "绿篱"])),
    item('christmas-trees', 'special', PAGE,
         ("Sapins de Noel", "Christmas trees", "圣诞树"),
         ("Les sapins naturels sont ramasses lors d'une collecte en janvier (le 14 janvier en 2026), sans "
          "inscription: a l'horizontale pres de la rue, sans decoration, ni attache ni sac. Hors collecte: avec "
          "les branches ou a l'ecocentre.",
          "Real trees are picked up in one January collection (14 January in 2026), no registration: lying near "
          "the street, undecorated, not tied or bagged. Otherwise: with branch pickup or at the ecocentre.",
          "天然圣诞树在一月的一次收集中收走（2026 年是 1 月 14 日），无需登记：横放在路边，去掉装饰，"
          "不要捆绑或装袋。其他时间可随树枝收集或送到生态中心。"),
         (["sapin naturel"], ["real Christmas tree"], ["真圣诞树"]),
         (["sapin", "noel"], ["christmas tree", "christmas"], ["圣诞树"])),
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
                   f"values (@bb, {q(i['key'])}, {q(i['dest'])}, {q(BIN[i['dest']])}, {q(i['src'])});")
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
    out.append("-- Last, and only for what was loaded.\n"
               "update municipality set guide_verified = true where id = @bb;\n")
    return "\n".join(out)


HEADER = """-- --------------------------------------------------------------------------
-- Boisbriand's sorting guide.
--
-- GENERATED by docs/verification/generate_v28_boisbriand_guide.py; edit
-- that, not this. {n} items, three languages.
--
-- Boisbriand publishes no list of its own on its site: its "where does it
-- go" link is RECYC-QUEBEC's "Ca va ou?" app, and no waste regulation with
-- annexes is linked the way Sainte-Therese's is. So the guide is built from
-- what the city does print:
--   * its 2026 collections page (saved by hand; the site refuses automated
--     clients): the brown bin's three questions, no plastic bags of any kind,
--     branch sizes, the 2026 dates, and that the ecocentre is Sainte-
--     Therese's;
--   * pages 3-5 of its 2025 calendar - the edition V25 uses at the owner's
--     direction - for the item lists: the blue bin's examples and
--     exceptions, the brown bin's list, the definition of the garbage bin,
--     the refused-items list. Where the 2026 page speaks it agrees.
--   * Sainte-Therese's ecocentre page, for hours and what it takes.
-- Cards cite the 2026 page (the ecocentre's cite its page): it is the city's
-- current word and a resident can open it. The 2025 PDF's own address was
-- not recorded.
--
-- What residents might not guess, and this guide says: pet litter, animal
-- waste, chicken manure and cooled ashes go in the BROWN bin; invasive
-- plants and waxed paper go in the GARBAGE; take-out containers go in the
-- BLUE bin, but aerosols, protective foam and pots and pans do not.
--
-- Not loaded: syringes. The calendar says only that they do not go in the
-- blue bin, never where they do.
-- --------------------------------------------------------------------------

set @bb = (select id from municipality where slug = 'boisbriand');
"""

if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    target = os.path.join(root, 'backend/src/main/resources/db/migration/V28__boisbriand_sorting_guide.sql')
    io.open(target, 'w', encoding='utf-8', newline='\n').write(render())
    print(f'wrote {target}: {len(ITEMS)} items')
