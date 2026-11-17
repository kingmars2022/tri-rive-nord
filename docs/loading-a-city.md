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
3. **Write the migration.** `V23` onward (`V22` is Sainte-Therese). Insert the sectors into
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
   Sainte-Therese, 230 after it.

## Loaded so far

| City | Calendar | Guide | Migration |
|---|---|---|---|
| Blainville | yes | yes | V15, V17 |
| Sainte-Therese | yes, 2026 grids | no | V22 |
| Boisbriand | no | no | |
| Rosemere | no | no | |

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

It is a downloader, not a parser. It follows only links on the city's own host
that look like they are about collection, honours robots.txt, and waits two
seconds between requests. What it saves is the input to step 2 above, not a
substitute for it.
