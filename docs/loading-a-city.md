# Loading a municipality

What has to happen before a city stops saying "calendar not loaded yet".
Written after the four-city architecture landed with one city's data in it,
for whoever does the second one -- which may well be a session that remembers
none of this.

## The rule that matters

**Nothing goes in that was not read from that city's own published document.**

Not inferred from a neighbouring town, not carried over from Blainville, not
reconstructed from how collection "usually" works on the Rive-Nord. The
sorting rules genuinely differ between these municipalities -- that difference
is the reason this application exists -- and a rule inherited from a city
whose document nobody read is an answer that is confident and wrong.

This is not hypothetical. `V15` exists because the calendar printed the wrong
bin for weeks: two collection patterns were labelled recycling where Blainville
prints household waste, and every test passed, because every test compared the
calendar against the database it had been generated from rather than against
the city's own calendar. Guessing three more cities would reproduce that at
four times the size.

If a city's document cannot be found or cannot be read, the right outcome is
that the city stays unloaded. The application already renders that honestly.

## Order of work

1. **Find the document.** Start from the city's collections page. What is
   wanted is the printed annual calendar, usually a PDF, plus whatever page
   lists what goes in which bin. Record the URL -- every row carries a
   `source_url`, and a row without one is a row nobody can check.
2. **Read it, do not skim it.** The things that are easy to get wrong:
   - which weekday each collection falls on, per sector
   - whether recycling and household waste alternate, and on which week
   - how the city divides itself, if it does at all (Blainville splits at a
     boulevard; another city may use three zones, or none)
   - statutory holidays, and how many days a collection moves when one lands
     on its day
3. **Write the migration.** `V28` onward (`V22` and `V23` are Sainte-Therese, `V24` a Blainville
   search fix that loading it made necessary, `V25` and `V26` Boisbriand, `V27` per-city notices). Insert the sectors into
   `municipality_sector`, the rules into `collection_schedule_rule`, the
   holidays into `collection_holiday`, and the guide into `sorting_item` and
   its three child tables. Set `calendar_verified` and `guide_verified` on the
   `municipality` row **last**, and only for the parts actually loaded: they
   are separate flags because the two documents arrive separately.
4. **Write a test that reads the document, not the database.** This is the
   step the calendar defect was about. Assert specific dates and bins taken
   from the published calendar, and prove the test fails without the
   migration before believing it.
5. **Run everything.** `mvn test -Pintegration-test` needs a real MySQL and
   Redis; see `docs/deployment.md`. All green before and after: 216 before
   Sainte-Therese, 243 after its calendar and guide, 254 after Boisbriand and its street table,
   259 after the admin console learned each city's sectors, 265 after notices became per city. Then run
   `docs/verification/guide_rank_sweep.py` for every city already loaded, before
   and after, and compare (see below).

## Loaded so far

| City | Calendar | Guide | Migration |
|---|---|---|---|
| Blainville | yes | yes | V15, V17 |
| Sainte-Therese | yes, 2026 grids | yes, regulation 1373 | V22, V23 |
| Boisbriand | **projected from the 2025 calendar** -- not checked against 2026, see below | no | V25 |
| Rosemere | no -- site does not answer automated requests | no | |

## What Sainte-Therese added to the list of things to get wrong

- **Anchor on the first collection on or after the day you read the
  calendar.** A rule is never projected backwards from its anchor, so an
  anchor one fortnight too late silently drops the collection in between. The
  first draft of V22 did exactly that to zone A's recycling on 17 November,
  and only the test that lists every printed date caught it.
- **Frequency can change with the season.** Sainte-Therese collects organics
  weekly April to November and fortnightly December to March. Rules have a
  `valid_until` for this: write the season as consecutive rules, each ending
  where the next begins.
- **A holiday can move a collection earlier.** `shift_days` is negative when
  the city moves a collection to the day before, which is what Sainte-Therese
  does with a Friday holiday. The same city moved 1 January 2026 the other
  way, so read the direction off the grid each time.
- **A pattern that is not weekly is not a rule.** Bulky items on "the first
  Monday of the month" cannot be an anchor plus weeks; load the printed dates
  that are still ahead as plain `collection_event` rows.
- **Tests written for one city may assume there is only one.** Loading the
  second city broke four existing integration tests, none of them because
  the data was wrong: they counted or deleted `collection_event` rows across
  the whole table. One deleted every city's collections from a date onward,
  which took Sainte-Therese's hand-loaded bulky pickup with it. Scope test
  SQL to the city it is about.
- **Say where the printed calendar ends.** The rules continue past it on the
  strength of the city's own standing rules, which is a weaker claim than a
  printed date. The migration and the test both say which dates are which.

## Conventions this schema expects

- **`item_key`** is a slug shared across cities for the same logical item --
  `pizza-box`, `paint`, `christmas-tree`. Reuse Blainville's key when the item
  is the same thing, even when the answer differs. That shared key is what
  makes "which city disagrees about this material" a query rather than a
  discovery, and it is the whole reason the guides are duplicated per city
  instead of inherited.
- **Sectors are per city.** `all` means the whole territory and is a row like
  any other. Do not assume `north`/`south` exists anywhere but Blainville.
- **A resident's default sector must never be `all`.** The upcoming query
  reads `sector = ? or sector = 'all'`, so a resident sitting on the wildcard
  matches only city-wide collections and never sees the garbage or recycling
  for their own half. `MunicipalityService.defaultSectorOf` skips it; a
  migration that gives a city only an `all` sector quietly recreates the bug.
- **French in this repository is written without accents** in seeded data, to
  match what is already there.

## Network

The municipal sites are not reachable from the default environment. A session
that needs them has to be started on the **Municipal data (North Shore)**
environment, which allows `blainville.ca`, `ville.sainte-therese.qc.ca`,
`boisbriand.qc.ca` and `ville.rosemere.qc.ca` -- though Sainte-Therese's site
is actually `www.sainte-therese.ca`, which that environment does not allow;
`ville.sainte-therese.qc.ca` times out. Failing that, the documents can
be downloaded by hand and uploaded, which is how Blainville's were read.

### Fetching the documents through GitHub Actions

When neither works, `.github/workflows/fetch-municipal-sources.yml` downloads
them from a GitHub runner, which can reach the cities' sites. List the city and
some candidate start URLs in `tools/municipal-fetch/request.json` and push. The
workflow runs on any branch where that file changed (it can also be started by
hand from the Actions tab), and commits the raw pages and PDFs back to the same
branch under `data/municipal-sources/<slug>/<date>/`, along with a
`manifest.json` that gives each file's source URL and sha256. `git pull`
afterwards.

**Boisbriand refuses it.** On 2026-11-17 the city's site,
`www.ville.boisbriand.qc.ca`, answered the runner with 403 Forbidden;
`boisbriand.qc.ca` and `www.boisbriand.qc.ca` do not resolve (the North Shore
environment allows the wrong one). The manifest under
`data/municipal-sources/boisbriand/2026-11-17/` records it. A 403 is the site
saying no, so the fetcher does not retry with a browser's identity. Boisbriand's
calendar and regulation have to be downloaded by hand in a browser and added
under `data/municipal-sources/boisbriand/`, with their URLs, which is how
Blainville's were read.

Tried again the same day from a session whose network allows the host (the
personal environment **tri-rive-nord** allows all four cities' real domains):
the 403 is an **Azure WAF JS Challenge** page, a bot check that only lets a
real browser through. `robots.txt` does not forbid the collections pages, but
the challenge is an explicit anti-bot measure, so it is not to be solved with
a headless browser either.

**Rosemere does not answer automated clients at all** on 2026-11-17: from the
GitHub runner both `ville.rosemere.qc.ca` and `www.ville.rosemere.qc.ca` time
out, and from the session the connection is reset; `rosemere.ca` does not
resolve. Its documents need the same manual download.

It is a downloader, not a parser. It follows only links on the city's own host
that look like they are about collection, honours robots.txt, and waits two
seconds between requests. What it saves is the input to step 2 above, not a
substitute for it.

## What Sainte-Therese's guide added

- **The city's guide may not be on the city's site.** Sainte-Therese's
  "Matieres acceptees" buttons open RECYC-QUEBEC's "Ca va ou?" app. Its
  waste-management regulation (1373 N.S.) has the binding lists in annexes A
  to E, and V23 is built from those. Look for the regulation before following
  a link off the city's domain.
- **Generate the guide; do not type it.** `docs/verification/generate_v23_sainte_therese_guide.py`
  writes V23 and checks the keyword length rule as it goes.
- **Loading one city's guide can move another city's answers.** MySQL scores
  full-text matches with word statistics from the whole index, and the
  city filter is applied after scoring. V23 cut Blainville's lead for
  "Where does soiled cardboard go?" from 10.7 vs 8.3 to 6.10 vs 6.09, and one
  test run in two then gave the wrong card; V24 gave that entry the keywords
  of its own name. `docs/verification/guide_rank_sweep.py` searches every
  entry by its own name and reports lost top spots and halved leads: run it
  for each loaded city before and after loading a new one.
- **The search has three blind spots that look like data problems:**
  - it does not stem, so `oeuf` never matches `oeufs`; add plurals;
  - keyword scores are summed per entry, so a word repeated across one
    entry's keywords ("pet food", "dog food", "cat food") pulls every
    question containing it to that entry;
  - the query normaliser strips function words, including the English "can"
    and words under three letters, so "Food can" searched as "food" and
    "Plastique no 7" as "plastique". Name entries with words that survive.
  `SainteThereseGuideIntegrationTest.everyEntryIsTheTopAnswerForItsOwnName`
  checks all three for a whole guide at once; copy it for the next city.
- **When the city's documents disagree, say so instead of choosing quietly.**
  The regulation and the collections page give different branch sizes for
  the brown bin, and different homes for appliances. V23's header records
  both and which one each entry follows.
- **Correct your own earlier migration in the next one, not in place.** V22
  said the page gives no rule for bulky items; it does ("le 1er lundi du
  mois"). Editing an applied migration breaks Flyway's checksum, so V23 says
  so and loads the dates.
- **Each test class adds a database pool.** Spring keeps one context per
  integration test class, and each held ten connections; at seventeen classes
  that is more than MySQL's default 151. `IntegrationEnvironment` now caps
  test pools at four.

## Boisbriand: loaded against the rule, on purpose

V25 is the one city loaded without its own document for the period. The 2026
calendar PDF could not be obtained (the site serves automated clients an Azure
WAF challenge, and the copy downloaded by hand was the 2025 edition), and the
owner chose on 2026-11-17 to load from 2025 rather than wait. So:

- The structure (sectors A and B, weekday by street, which bins share a week,
  winter organics, holidays a day earlier) is the 2025 calendar's.
- The phase is the end of 2025 carried forward a fortnight at a time. If the
  city restarted its cycle in 2026, every date is a week off, and nothing
  available here can tell.
- Every resident-facing note says the schedule comes from the 2025 calendar
  and points to boisbriand.ca.
- `BoisbriandCalendarIntegrationTest` checks that the migration does what the
  2025 calendar describes. It cannot check 2026.
- Residents find their sector by typing their street on the settings page.
  The table behind it (V26, 252 streets and civic ranges) is parsed from the
  same 2025 PDF by `docs/verification/generate_v26_boisbriand_streets.py`,
  which refuses to write unless every weekday cell on the page is paired with
  exactly one street. Like V25, it is 2025's; replace it with V25.

**First thing to do when the 2026 (or 2027) calendar is in hand:** read it
against V25 and replace the rules. That is the step this city skipped.

## The admin side reads sectors from the city too

The admin console's pickers, the admin agent's tool schema and its system
prompt were all written for Blainville (north/south, "Blainville, Quebec").
They now come from the selected city, and the server refuses an admin or
agent write naming a sector the city does not have (400, listing the real
ones). A new city needs no admin code, only its `municipality_sector` rows.

## Notices are per city too

Since V27 a notice belongs to one city: the public list, the admin list, the
agent's notice tools, the inbox fan-out and browser push all stay inside it.
Nothing to do when loading a city; `NoticeScopingIntegrationTest` holds the
line.

## Sectors a resident cannot name

A city that assigns sectors street by street needs a street table
(`municipality_street`), and the settings page offers the lookup whenever the
city has one (`hasStreetDirectory`). Each street's sector must exist (a foreign
key) and must have collection rules (`BoisbriandStreetDirectoryIntegrationTest`);
otherwise a resident lands on an empty calendar.
