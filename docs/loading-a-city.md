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
3. **Write the migration.** `V22` onward. Insert the sectors into
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
   Redis; see `docs/deployment.md`. 216 tests, all green, before and after.

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
`boisbriand.qc.ca` and `ville.rosemere.qc.ca`. Failing that, the documents can
be downloaded by hand and uploaded, which is how Blainville's were read.
