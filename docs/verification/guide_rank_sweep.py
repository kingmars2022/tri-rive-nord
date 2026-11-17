# -*- coding: utf-8 -*-
"""Does every sorting-guide entry still win a search for its own name?

Run it before and after loading a city, and compare. MySQL scores full-text
matches with word statistics from the whole index, not from one city's rows,
so loading one city's guide can reorder another city's results even though
every search is filtered to a single city. V24 exists because loading
Sainte-Therese (V23) turned one of Blainville's answers into a near-tie.

For each entry of the city, it searches that entry's own name, in its own
language, the way SortingSearchMapper.xml does (text score plus keyword
score), and records the top hit and its lead over the runner-up.

    python3 docs/verification/guide_rank_sweep.py <slug> <out.json> [<before.json>]

With a third argument it compares against an earlier run and lists entries
that lost their top spot or whose lead more than halved. Talks to the MySQL
in the `it-mysql` container used for the integration tests.
"""
import json
import subprocess
import sys

DATABASE = 'bienvenue_blainville_test'


def sql(query):
    result = subprocess.run(
        ['docker', 'exec', '-i', 'it-mysql', 'mysql', '-uroot', '-pRootLocalPass123!', '-N', '-B',
         '--default-character-set=utf8mb4', DATABASE],
        input=query, capture_output=True, text=True)
    return [line.split('\t') for line in result.stdout.strip().split('\n') if line]


def sweep(slug):
    city = sql(f"select id from municipality where slug = '{slug}';")[0][0]
    names = sql(f"""select t.item_id, t.language_code, t.name from sorting_item_translation t
                    join sorting_item i on i.id = t.item_id
                    where i.municipality_id = {city} order by t.item_id, t.language_code;""")
    out = {}
    for item_id, lang, name in names:
        text = 't.search_ngram' if lang == 'zh' else 't.name, t.instruction'
        keyword = 'keyword_ngram' if lang == 'zh' else 'keyword'
        query = name.replace('\\', '').replace("'", "''")
        rows = sql(f"""set @q = '{query}';
            select t.item_id,
                   round(match({text}) against (@q in natural language mode) + coalesce(k.ks, 0), 3) s
            from sorting_item_translation t join sorting_item i on i.id = t.item_id
            left join (select item_id, sum(match({keyword}) against (@q in natural language mode)) ks
                       from sorting_item_keyword where language_code = '{lang}' group by item_id) k
                   on k.item_id = t.item_id
            where t.language_code = '{lang}' and i.municipality_id = {city}
            having s > 0 order by s desc limit 2;""")
        top = rows[0][0] if rows else None
        lead = (round(float(rows[0][1]) - (float(rows[1][1]) if len(rows) > 1 else 0), 3)
                if rows else None)
        out[f'{item_id}/{lang}'] = (name, top, lead)
    return out


if __name__ == '__main__':
    slug, target = sys.argv[1], sys.argv[2]
    now = sweep(slug)
    json.dump(now, open(target, 'w'), ensure_ascii=False, indent=0)
    lost = {k: v for k, v in now.items() if v[1] != k.split('/')[0]}
    print(f'{len(now)} searches; {len(lost)} where the entry is not its own top hit: {lost}')
    if len(sys.argv) > 3:
        before = json.load(open(sys.argv[3]))
        halved = [(k, before[k][2], now[k][2]) for k in before
                  if k in now and before[k][2] and now[k][2] is not None and now[k][2] < before[k][2] * 0.5]
        print('lead more than halved since the earlier run:', halved)
