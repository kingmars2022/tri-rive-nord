# -*- coding: utf-8 -*-
"""Emits V25: Boisbriand's collection calendar, projected from its 2025 calendar.

READ THIS FIRST. docs/loading-a-city.md says nothing goes in that was not read
from the city's own document for the period it covers. This migration breaks
that rule on purpose, at the owner's explicit direction (2026-11-17): the 2026
calendar PDF could not be obtained, because the city's site answers automated
clients with an Azure WAF bot challenge and the copy downloaded by hand was the
2025 edition. So:

  * The STRUCTURE comes from "Calendrier des collectes 2025" (8 pages, archived
    under data/municipal-sources/boisbriand/2026-11-17/):
      - two sectors, A and B; the weekday (Tuesday to Friday) is set per street;
      - recycling every other week, the same weeks in both sectors;
      - garbage: sector A in the recycling weeks, sector B in the others;
      - organics weekly April to November; December to March every other week,
        in the non-recycling weeks, in both sectors;
      - collections due 25 December and 1 January move a day EARLIER.
  * The PHASE is carried forward from the end of 2025: recycling weeks began
    Monday 8 and 22 December 2025, so every 14 days after - the week of
    23 November 2026 is one. If the city restarted the cycle in 2026, every
    date here is one week off. Nothing published for 2026 confirms or refutes
    it; the city's lookup tool (collectes.boisbriand.ca) was not consulted.
  * The 2026 FACTS come from the city's 2026 collections page, saved by hand:
    the frequencies (which agree with the 2025 structure) and the excess-waste
    week of 15-18 December 2026.

Every resident-facing note says the schedule was established from the 2025
calendar and points to boisbriand.ca.

    python3 docs/verification/generate_v25_boisbriand_calendar.py
"""
import datetime as dt
import io
import os

PAGE = 'https://www.ville.boisbriand.qc.ca/services/citoyens/collectes-des-matieres-residuelles'

# The day before the calendar was loaded; the top-up generates from here.
LOADED_THROUGH = dt.date(2026, 11, 16)
# Monday of a recycling week, carried forward from 22 December 2025.
RECYCLING_WEEK = dt.date(2026, 11, 23)
assert (RECYCLING_WEEK - dt.date(2025, 12, 22)).days % 14 == 0
assert RECYCLING_WEEK.weekday() == 0

WEEKDAYS = [  # offset from Monday, code, fr, en, zh
    (1, 'mardi', 'mardi', 'Tuesday', '周二'),
    (2, 'mercredi', 'mercredi', 'Wednesday', '周三'),
    (3, 'jeudi', 'jeudi', 'Thursday', '周四'),
    (4, 'vendredi', 'vendredi', 'Friday', '周五'),
]

CAVEAT_FR = " Horaire etabli d'apres le calendrier 2025 de la Ville; verifiez votre journee sur boisbriand.ca."
CAVEAT_EN = " Schedule worked out from the city's 2025 calendar; check your day on boisbriand.ca."
CAVEAT_ZH = "（根据市政府 2025 年日历推算，请在 boisbriand.ca 核对。）"


def q(s):
    return 'null' if s is None else "'" + s.replace("'", "''") + "'"


def first_on_or_after(day, weekday_offset):
    return day + dt.timedelta(days=(weekday_offset - day.weekday()) % 7)


def rules():
    out = []
    for sector in ('a', 'b'):
        for offset, code, fr, en, zh in WEEKDAYS:
            s = f'{sector}-{code}'
            S = sector.upper()
            recycling_day = RECYCLING_WEEK + dt.timedelta(days=offset)
            other_day = recycling_day - dt.timedelta(days=7)  # this week: a non-recycling week
            assert other_day > LOADED_THROUGH
            garbage_day = recycling_day if sector == 'a' else other_day
            with_what = ('en meme temps que le recyclage', 'the same week as recycling', '与可回收物同一周') \
                if sector == 'a' else ('en alternance avec le recyclage', 'alternating with recycling', '与可回收物交替')

            out.append((s, 'recycling', 'blue', recycling_day, 2, None,
                        f"Matieres recyclables, le {fr} aux deux semaines.",
                        f"Recycling, every other {en}.",
                        f"可回收物，隔周{zh}收集。"))
            out.append((s, 'garbage', 'black', garbage_day, 2, None,
                        f"Dechets, le {fr} aux deux semaines, {with_what[0]}.",
                        f"Garbage, every other {en}, {with_what[1]}.",
                        f"生活垃圾，隔周{zh}收集，{with_what[2]}。"))
            # Organics: weekly to the end of November, then every other week in
            # the non-recycling weeks, then weekly again from April.
            out.append((s, 'organic', 'brown', other_day, 1, dt.date(2026, 11, 30),
                        f"Matieres compostables, chaque {fr} d'avril a novembre.",
                        f"Compost, every {en} from April to November.",
                        f"可堆肥物，4 月至 11 月每{zh}收集。"))
            winter = other_day + dt.timedelta(days=14)
            assert winter.month == 12
            out.append((s, 'organic', 'brown', winter, 2, dt.date(2027, 3, 31),
                        f"Matieres compostables, un {fr} sur deux de decembre a mars.",
                        f"Compost, every other {en} from December to March.",
                        f"可堆肥物，12 月至 3 月隔周{zh}收集。"))
            spring = first_on_or_after(dt.date(2027, 4, 1), offset)
            out.append((s, 'organic', 'brown', spring, 1, dt.date(2027, 11, 30),
                        f"Matieres compostables, chaque {fr} d'avril a novembre.",
                        f"Compost, every {en} from April to November.",
                        f"可堆肥物，4 月至 11 月每{zh}收集。"))
            for r in out[-5:]:
                assert r[3].weekday() == offset, r
    return out


def render():
    lines = ['-- ' + l if l else '--' for l in __doc__.strip().split('\n')]
    lines = ['-- GENERATED by docs/verification/generate_v25_boisbriand_calendar.py; edit that, not this.',
             '--'] + lines + ['', "set @bb = (select id from municipality where slug = 'boisbriand');",
                              f"set @page = {q(PAGE)};", '', "update municipality set source_url = @page where id = @bb;", '']

    lines.append('insert into municipality_sector (municipality_id, code, position, name_fr, name_en, name_zh, source_url) values')
    sectors = ["(@bb, 'all', 0, 'Tout le territoire', 'Whole city', '全市', @page)"]
    pos = 1
    for sector in ('a', 'b'):
        for offset, code, fr, en, zh in WEEKDAYS:
            sectors.append(f"(@bb, '{sector}-{code}', {pos}, 'Secteur {sector.upper()}, {fr}', "
                           f"'Sector {sector.upper()}, {en}', '{sector.upper()} 区，{zh}', @page)")
            pos += 1
    lines.append(',\n'.join(sectors) + ';')
    lines.append('')

    lines.append('insert into collection_schedule_rule\n(municipality_id, sector, collection_type, bin_color, '
                 'anchor_date, interval_weeks, valid_until,\n generated_through, note_fr, note_en, note_zh, source_url)\nvalues')
    rows = []
    for s, kind, colour, anchor, weeks, until, fr, en, zh in rules():
        rows.append(f"(@bb, '{s}', '{kind}', '{colour}', '{anchor}', {weeks}, {q(until.isoformat() if until else None)}, "
                    f"'{LOADED_THROUGH}',\n {q(fr + CAVEAT_FR)},\n {q(en + CAVEAT_EN)},\n {q(zh + CAVEAT_ZH)}, @page)")
    lines.append(',\n'.join(rows) + ';')
    lines.append('')

    lines.append("""-- The 2025 calendar moves collections due 25 December and 1 January a day
-- earlier ("Collectes du 25 decembre 2025 devancees au 24 decembre 2025").
-- Both fall on a Friday in 2026-27; carried forward as the same rule.
insert into collection_holiday
(municipality_id, holiday_date, name_fr, name_en, name_zh, shift_days, sector, source_url) values
(@bb, '2026-12-25', 'Noel', 'Christmas Day', '圣诞节', -1, 'all', @page),
(@bb, '2027-01-01', 'Jour de l''An', 'New Year''s Day', '元旦', -1, 'all', @page);
""")

    lines.append("-- From the 2026 page itself: excess waste, \"Du 15 au 18 decembre 2026\", on\n"
                 "-- each sector's usual day. Rows, not a rule: the page lists the weeks.")
    lines.append('insert into collection_event\n(municipality_id, collection_date, sector, collection_type, bin_color, '
                 'note_fr, note_en, note_zh, source_url)\nvalues')
    ev = []
    for sector in ('a', 'b'):
        for offset, code, fr, en, zh in WEEKDAYS:
            day = first_on_or_after(dt.date(2026, 12, 15), offset)
            assert dt.date(2026, 12, 15) <= day <= dt.date(2026, 12, 18)
            ev.append(f"(@bb, '{day}', '{sector}-{code}', 'bulky', 'none',\n"
                      f" 'Collecte des dechets excedentaires: 23 kg par sac ou paquet, 5 m3 par adresse, aucun bac vide.',\n"
                      f" 'Excess waste collection: 23 kg per bag or bundle, 5 m3 per address, no bins emptied.',\n"
                      f" '超量垃圾收集：每袋或每捆不超过 23 公斤，每个地址最多 5 立方米，不清空垃圾桶。', @page)")
    lines.append(',\n'.join(ev) + ';')
    lines.append('')
    lines.append("""-- Shown to residents, because the owner chose to load it. What the flag
-- means for this city is in the header: loaded from the 2025 calendar, not
-- checked against 2026. The sorting guide is not loaded.
update municipality set calendar_verified = true where id = @bb;
""")
    return '\n'.join(lines)


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    target = os.path.join(root, 'backend/src/main/resources/db/migration/V25__boisbriand_collection_calendar_from_2025.sql')
    io.open(target, 'w', encoding='utf-8', newline='\n').write(render())
    print('wrote', target, len(rules()), 'rules')
