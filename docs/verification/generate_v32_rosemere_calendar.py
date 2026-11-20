# -*- coding: utf-8 -*-
"""Emits V32: Rosemere's collection calendar, read from its 2026 municipal calendar.

Source: the Town of Rosemere's "Calendrier 2026" (32 pages, bilingual),
archived as data/municipal-sources/rosemere/2026-11-20/manual_calendrier-municipal-2026.pdf.

What the document says, and where:
  * Page 31: four collection sectors on a map (not the six council sectors of
    page 2); garbage and recycling every other week all year; organics and
    green waste weekly from May to November 2026, every other week from
    January to April 2026 and from December 2026 to April 2027.
  * Pages 7-29, one grid per month: sector 1 on Tuesday, 2 on Wednesday,
    3 on Thursday, 4 on Friday; recycling in the weeks of 5 January,
    19 January... 23 November, 7 and 21 December; garbage in the other weeks;
    organics on Thursday for every sector (weekly through 26 November, then
    10 and 24 December). Friday 25 December's recycling is printed on
    Thursday 24. Large items are collected Monday 14 December.
  * Not printed: anything in 2027, including when sector 4's garbage due
    Friday 1 January 2027 is collected. The 2026 rules stop at 31 December;
    the 2027 rules carry the same rhythm on from their next occurrence after
    1 January and say so, and that one Friday is left out rather than guessed.

Rules start at the first printed date on or after the day the calendar was
read (20 November 2026), and every mark is the day before.

    python3 docs/verification/generate_v32_rosemere_calendar.py
"""
import datetime as dt
import io
import os

import pg_sql

READ = dt.date(2026, 11, 20)
LOADED_THROUGH = READ - dt.timedelta(days=1)
SITE = 'https://www.ville.rosemere.qc.ca/'
END_2026 = dt.date(2026, 12, 31)

# sector -> (weekday name fr, en, zh, first garbage date on/after READ, first recycling date)
SECTORS = [
    ('secteur-1', 'mardi', 'Tuesday', '周二', dt.date(2026, 12, 1), dt.date(2026, 11, 24)),
    ('secteur-2', 'mercredi', 'Wednesday', '周三', dt.date(2026, 12, 2), dt.date(2026, 11, 25)),
    ('secteur-3', 'jeudi', 'Thursday', '周四', dt.date(2026, 12, 3), dt.date(2026, 11, 26)),
    ('secteur-4', 'vendredi', 'Friday', '周五', dt.date(2026, 11, 20), dt.date(2026, 11, 27)),
]

Y26_FR = " D'apres le calendrier municipal 2026 de la Ville."
Y26_EN = " From the town's 2026 municipal calendar."
Y26_ZH = "（依据市政府 2026 年市政日历。）"
Y27_FR = " Prolonge au rythme du calendrier municipal 2026 en attendant celui de 2027; verifiez sur ville.rosemere.qc.ca."
Y27_EN = " Continued at the rhythm of the town's 2026 calendar until the 2027 one is out; check on ville.rosemere.qc.ca."
Y27_ZH = "（按市政府 2026 年日历的节奏延续，2027 年日历公布前请在 ville.rosemere.qc.ca 核对。）"
WINTER27_FR = (" Frequence d'hiver du calendrier municipal 2026 (decembre 2026 a avril 2027); dates prolongees"
               " au meme rythme, verifiez sur ville.rosemere.qc.ca.")
WINTER27_EN = (" Winter frequency from the town's 2026 calendar (December 2026 to April 2027); dates continued"
               " at the same rhythm, check on ville.rosemere.qc.ca.")
WINTER27_ZH = "（冬季频率依据市政府 2026 年日历（2026 年 12 月至 2027 年 4 月），日期按同一节奏延续，请在 ville.rosemere.qc.ca 核对。）"


def q(v):
    if v is None:
        return 'null'
    if isinstance(v, dt.date):
        return f"'{v.isoformat()}'"
    return "'" + str(v).replace("'", "''") + "'"


def next_after(anchor, weeks, day):
    """First occurrence of an anchor's rhythm strictly after `day`."""
    step = dt.timedelta(weeks=weeks)
    d = anchor
    while d <= day:
        d += step
    return d


def rules():
    out = []
    for code, fr, en, zh, garbage, recycling in SECTORS:
        n = code[-1]
        g = (f"Dechets (bac gris), le {fr} aux deux semaines, secteur de collecte {n}.",
             f"Garbage (grey bin), every other {en}, collection sector {n}.",
             f"生活垃圾（灰桶），收集 {n} 区，隔周{zh}。")
        r = (f"Matieres recyclables (bac bleu), le {fr} aux deux semaines, en alternance avec les dechets,"
             f" secteur de collecte {n}.",
             f"Recycling (blue bin), every other {en}, alternating with garbage, collection sector {n}.",
             f"可回收物（蓝桶），收集 {n} 区，隔周{zh}，与生活垃圾交替。")
        for kind, colour, first, note in (('garbage', 'grey', garbage, g), ('recycling', 'blue', recycling, r)):
            out.append((code, kind, colour, first, 2, END_2026, note, (Y26_FR, Y26_EN, Y26_ZH)))
            out.append((code, kind, colour, next_after(first, 2, dt.date(2027, 1, 1)), 2, None, note,
                        (Y27_FR, Y27_EN, Y27_ZH)))
    summer = ("Matieres organiques et residus verts (bac brun), chaque jeudi de mai a novembre, tous les secteurs.",
              "Organics and green waste (brown bin), every Thursday from May to November, all sectors.",
              "有机垃圾和庭院废料（棕桶），5 月至 11 月每周四收集，所有区相同。")
    winter = ("Matieres organiques et residus verts (bac brun), un jeudi sur deux de decembre a avril, tous les secteurs.",
              "Organics and green waste (brown bin), every other Thursday from December to April, all sectors.",
              "有机垃圾和庭院废料（棕桶），12 月至 4 月隔周周四收集，所有区相同。")
    out.append(('all', 'organic', 'brown', dt.date(2026, 11, 26), 1, dt.date(2026, 11, 26), summer,
                (Y26_FR, Y26_EN, Y26_ZH)))
    out.append(('all', 'organic', 'brown', dt.date(2026, 12, 10), 2, END_2026, winter, (Y26_FR, Y26_EN, Y26_ZH)))
    out.append(('all', 'organic', 'brown', dt.date(2027, 1, 7), 2, dt.date(2027, 4, 30), winter,
                (WINTER27_FR, WINTER27_EN, WINTER27_ZH)))
    out.append(('all', 'organic', 'brown', dt.date(2027, 5, 6), 1, dt.date(2027, 11, 25), summer,
                (Y27_FR, Y27_EN, Y27_ZH)))
    return out


def render():
    lines = [
        '-- GENERATED by docs/verification/generate_v32_rosemere_calendar.py; edit that, not this.',
        '--',
    ] + ['-- ' + line if line else '--' for line in __doc__.strip().split('\n')] + [
        '',
        "set @rm = (select id from municipality where slug = 'rosemere');",
        f"set @site = {q(SITE)};",
        '',
        "update municipality set source_url = @site where id = @rm;",
        '',
        'insert into municipality_sector (municipality_id, code, position, name_fr, name_en, name_zh, source_url) values',
    ]
    sectors = ["(@rm, 'all', 0, 'Tout le territoire', 'Whole town', '全市', @site)"]
    for pos, (code, fr, en, zh, *_rest) in enumerate(SECTORS, start=1):
        n = code[-1]
        sectors.append(f"(@rm, '{code}', {pos}, 'Secteur de collecte {n} ({fr})', "
                       f"'Collection sector {n} ({en})', '收集 {n} 区（{zh}）', @site)")
    lines.append(',\n'.join(sectors) + ';')
    lines += ['', 'insert into collection_schedule_rule',
              '(municipality_id, sector, collection_type, bin_color, anchor_date, interval_weeks, valid_until,',
              ' generated_through, note_fr, note_en, note_zh, source_url)', 'values']
    rows = []
    for code, kind, colour, anchor, weeks, until, (fr, en, zh), (cfr, cen, czh) in rules():
        rows.append(f"(@rm, '{code}', '{kind}', '{colour}', {q(anchor)}, {weeks}, {q(until)}, {q(LOADED_THROUGH)},\n"
                    f" {q(fr + cfr)},\n {q(en + cen)},\n {q(zh + czh)}, @site)")
    lines.append(',\n'.join(rows) + ';')
    lines += ['', '-- Printed on 24 December: Friday 25 December\'s recycling (sector 4) a day earlier.',
              'insert into collection_holiday',
              '(municipality_id, holiday_date, name_fr, name_en, name_zh, shift_days, sector, source_url) values',
              "(@rm, '2026-12-25', 'Noel', 'Christmas Day', '圣诞节', -1, 'all', @site);",
              '',
              '-- Large items, Monday 14 December, printed without a sector: every sector.',
              'insert into collection_event',
              '(municipality_id, collection_date, sector, collection_type, bin_color, note_fr, note_en, note_zh, source_url)',
              'values',
              "(@rm, '2026-12-14', 'all', 'bulky', 'none',",
              " " + q("Collecte des objets encombrants, tous les secteurs." + Y26_FR) + ",",
              " " + q("Large items pickup, all sectors." + Y26_EN) + ",",
              " " + q("大件物品收集，所有区相同。" + Y26_ZH) + ", @site);",
              '',
              'update municipality set calendar_verified = true where id = @rm;',
              '']
    return '\n'.join(lines)


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    target = os.path.join(root, 'backend/src/main/resources/db/migration/V32__rosemere_collection_calendar.sql')
    io.open(target, 'w', encoding='utf-8', newline='\n').write(pg_sql.convert(render()))
    print('wrote', target, len(rules()), 'rules')
