"""Restore French accents in guide text: a reviewed word list plus context rules."""
import re
SAFE = dict(l.split() for l in """
absorbees absorbées|acceptees acceptées|acceptes acceptés|aerosol aérosol|aerosols aérosols|affichee affichée
annee année|aout août|apres après|aretes arêtes|attachees attachées|attaches attachés|aupres auprès
baton bâton|batonnets bâtonnets|batons bâtons|biodegradable biodégradable|biodegradables biodégradables
boite boîte|boites boîtes|brise brisé|brises brisés|buche bûche|buches bûches|cafe café|casse cassé|casses cassés
cassee cassée|cedre cèdre|cereales céréales|cerealiers céréaliers|chaussee chaussée|cires cirés|composee composée
congelateur congélateur|coniferes conifères|cosmetiques cosmétiques|coupee coupée|creme crème|cuisines cuisinés
cuisiniere cuisinière|deblais déblais|decembre décembre|dechets déchets|dechiquetage déchiquetage|dechiquete déchiqueté
decomposent décomposent|decoration décoration|defaites défaites|definit définit|degradables dégradables|degradent dégradent
deja déjà|delai délai|demolition démolition|deposer déposer|deposez déposez|depot dépôt|derniere dernière
detergent détergent|deuxieme deuxième|diametre diamètre|ecailles écailles|ecales écales|ecocentre écocentre
ecorce écorce|ecorces écorces|elaguees élaguées|elastique élastique|electrique électrique|electriques électriques
electromenager électroménager|electromenagers électroménagers|electroniques électroniques|ensaches ensachés
entree entrée|epicerie épicerie|equipement équipement|etre être|evier évier|evitez évitez|excedentaires excédentaires
excrements excréments|exigee exigée|expanse expansé|facon façon|fanees fanées|fenetres fenêtres|fevrier février
gateau gâteau|gateaux gâteaux|glacee glacée|gouttieres gouttières|hygiene hygiène|hygienique hygiénique
hygieniques hygiéniques|identite identité|imprimee imprimée|imprimes imprimés|lamine laminé|lamines laminés
legerement légèrement|legumes légumes|liege liège|litiere litière|malgre malgré|marque marqué|materiaux matériaux
matiere matière|matieres matières|meme même|menageres ménagères|metal métal|metalliques métalliques|metres mètres
nettoye nettoyé|noel Noël|numero numéro|ondule ondulé|pates pâtes|periode période|piece pièce|plastifie plastifié
plastifies plastifiés|pliees pliées|plutot plutôt|poele poêle|poeles poêles|polystyrene polystyrène|precedent précédent
premiere première|prepares préparés|pres près|prete prête|publiee publiée|quantite quantité|quantites quantités
ramasses ramassés|recuperation récupération|refrigerants réfrigérants|refrigerateur réfrigérateur|refuses refusés
reglement règlement|releves relevés|renouee renouée|renovation rénovation|reservez réservez|residence résidence
residus résidus|rosemere Rosemère|secheuse sécheuse|securitaire sécuritaire|separees séparées|separez séparez
souille souillé|souillee souillée|souilles souillés|speciales spéciales|televiseur téléviseur|television télévision
the thé|therese Thérèse|traite traité|traites traités|tres très|trie trié|tries triés|usagee usagée|usages usagés
uses usés|vegetaux végétaux|velo vélo|vetements vêtements|video vidéo
""".strip().replace('\n', '|').split('|'))
PHRASES = [  # context rules, applied before the word list
    (r"\b([Pp]apiers?|[Cc]artons?) cire\b", r"\1 ciré"),
    (r"\b(est|plus|pas) accepte\b", r"\1 accepté"),
    (r"\b(est|a) ramasse\b", r"\1 ramassé"),
    (r"\best refuse\b", "est refusé"),
    (r"\bFerme du\b", "Fermé du"),
    (r"\bdes (\d)", r"dès \1"),
    (r"\ba cote du\b", "à côté du"),
    (r"\bGrande-Cote\b", "Grande-Côte"),
]
VERB_BEFORE_A = {"qui", "n'", "ville", "il", "elle", "y", "on"}
PROTECT = re.compile(r"\S+@\S+|https?://\S+|\b[\w.-]+\.(?:ca|com|org|net|qc\.ca)\b\S*")

def _word(m):
    w = m.group(0); low = w.lower()
    if low not in SAFE: return w
    r = SAFE[low]
    if w[0].isupper() and not r[0].isupper(): r = r[0].upper() + r[1:]
    return r

ELISION = re.compile(r"(?<![\w'])(?<!\d )([dlsnjcmtDLSNJCMT]|[Qq]u|[Jj]usqu) (?=[aeiouyhAEIOUYH])")

def _plain(s):
    s = ELISION.sub(r"\1'", s)
    for pat, rep in PHRASES: s = re.sub(pat, rep, s)
    s = re.sub(r"\b([Qq]u|[Jj]usqu)'a\b", r"\1'à", s)
    s = re.sub(r"[A-Za-z]+", _word, s)
    out, prev = [], ""
    for tok in re.split(r"(\s+)", s):
        if tok == "a" and prev.lower() not in VERB_BEFORE_A and not prev.lower().endswith("n'"):
            tok = "à"
        if tok.strip(): prev = tok
        out.append(tok)
    return "".join(out)

def accent(s):
    if s is None: return None
    parts, last = [], 0
    for m in PROTECT.finditer(s):
        parts.append(_plain(s[last:m.start()])); parts.append(m.group(0)); last = m.end()
    parts.append(_plain(s[last:]))
    return "".join(parts)
