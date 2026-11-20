# Simulated residents, 20 November 2026

The four towns had just been loaded. Before calling the app finished, it was
used the way residents and an administrator would use it, on a local copy of
production: the same jar the Dockerfile builds (frontend inside), a fresh
PostgreSQL migrated by Flyway, and production's switches (`CACHE_TYPE=none`,
`EVENTS_ENABLED=false`, `ASSISTANT_PROVIDER=template`). Nothing was run
against the live site, so no fake accounts, notices or questions went into
its data.

Two parts:

- **88 questions to the assistant**, worded as residents type them -
  lowercase, no accents, plurals, brand names ("kleenex"), Quebec words
  ("chaudrons", "divan"), half-sentences ("what about kleenex"), and
  questions the guide cannot answer ("What time does the mayor's office
  open?") - across the four towns and three languages, each with the
  destination that town's own guide gives. Kept as
  `ResidentQuestionsIntegrationTest`.
- **Five people in a real browser** (Playwright): a Blainville resident in
  French on a desktop; a Rosemere resident in Chinese on a phone who picks
  sector 4; a Boisbriand resident in English who finds their sector by
  street; a Sainte-Therese resident who registers; an administrator on
  Rosemere. Each run recorded browser errors, failed API calls, untranslated
  message keys, pages wider than the screen, and a screenshot of every step,
  which were read one by one.

## What was wrong, and what changed

| Found | Why | Fix |
|---|---|---|
| "What time does the mayor's office open?" answered with Christmas trees (Blainville) | "open" occurs in that card's instruction | An entry must be named by the question - a word of its name or a keyword - before it can answer (`SortingSearchIndex`) |
| "old paint cans" sent to Rosemere's **blue bin** | "cans" matched the metal card | Multi-word keywords are phrases; Rosemere's metal card says "food can", "tin can"; a card for what its calendar refuses without saying where (hazardous waste, propane, aerosols...) gives the town's number (V34) |
| "vieux chaudrons", "pelures" missed entries listed in the singular | no stemming | One trailing "s" folds to the singular, on both sides |
| "old TV" found nothing | "TV" is under the three-letter minimum | "tv" and "tele" read as television |
| "pelures de patate", "divan", "kleenex", "laptop", "tree stump", "leaves and grass" missed | everyday words the guides do not use | Keywords on the entries the guides already give (V34) |
| Every town told Blainville's curbside hours | the sentence predates the other towns | Each town's own printed hours (V35); none shown for Rosemere, which prints none |
| "Put the bin out tonight" when the collection was days away | only "today" was distinguished | "tonight" only when the collection is tomorrow, "the evening before" otherwise |
| A photo button on the free deployment, where every upload fails | it needs AWS, which that deployment does not have | `PHOTO_ENABLED`, off by default; the page asks before offering the button, and the endpoints refuse plainly |
| An answer's sources ran together ("...d alimentsBoite de conserve") | links with no separator | Separated |
| "Reponse generee par template" under every answer | the provider id was shown | "Answer taken directly from the sorting guide" |
| "Rosemere", "Sainte-Therese", "Ou deposer", "Parametres" | names and French interface text stored without accents | Town names (V35) and the French interface strings spelled properly |
| Boisbriand's street lookup said its list was the 2025 calendar's | not updated when V31 checked it against 2026 | Says 2026 |

After the fixes all 88 questions answer as each town's guide does, and the
browser runs record no errors.

## Second pass: everything, systematically

The first pass followed five people. The second went through every
combination instead, again on the local copy:

- **Data**: every sector of every town (22) for each of the next 62 days,
  checked for duplicates, unknown bin types and days with no collection
  where the calendar has one; and all 169 guide entries, each in three
  languages, checked for empty names, instructions or sources.
- **Pages**: the five routes, in each town, each language, on a desktop and
  on a phone - 120 page loads - checked for browser errors, failed calls,
  raw message keys, `undefined`/`NaN`, pages wider than the screen, and
  navigation in the wrong language.
- **Administrator forms**: every form of the console (one-off collection,
  guide card, notice, closed day, then editing the new card) submitted for
  each town - 20 saves.
- **Accounts and edits through the API**: registration and login rules, the
  same email in other case, preferences and sectors, a resident on the admin
  API, a notice and who sees it and in whose inbox, a closed day moving a
  collection and coming back, a guide edit reaching the guide and the
  assistant, and bad requests.

Two more problems, both real:

| Found | Why | Fix |
|---|---|---|
| The admin console could not add or save any guide card: every attempt was refused (400) | the API required an `itemKey` the console never sends; the console's own tests mock the API | The key is made from the English name when none is given ("scented-candle", then "scented-candle-2"); an edit keeps its key (`AdminSortingConsoleIntegrationTest`) |
| A published notice never reached any resident's inbox on the free deployment, although the settings page promises it | only the Kafka consumer filled inboxes, and production runs without a broker | Without a broker, publishing the notice fills the inboxes in the same transaction, under the event's id, so a later broker relay cannot deliver it twice (`NoticeInboxWithoutBrokerIntegrationTest`) |

After these, every sweep records no problems: 0 in the data, 0 in 120 page
loads, 20 of 20 saves, 29 of 29 API checks, 88 of 88 questions.

## Left for later, then fixed

Three small things were first left alone, then fixed on their own:

| Was | Fix |
|---|---|
| The guide entries' French had no accents or apostrophes ("Papiers et cartons souilles d aliments"), as every migration had stored it | V36 restores them in 391 values across the four towns ("Papiers et cartons souillés d'aliments", "Fermé du 21 décembre", "dès 18 h"). It is generated by `docs/verification/generate_v36_french_accents.py` from a reviewed word list and context rules in `fr_accents.py`: words with two valid spellings ("a"/"à", "cote"/"côté", "ramasse"/"ramassé") change only in a context that settles them, and web and email addresses ("sainte-therese.ca") are never touched. Each update applies only while the value is still the old text, so an entry an administrator has since edited stays as they wrote it. Search folds accents, so questions typed either way still find the same entries. |
| The admin collection list began at its latest date (May 2027), so this week was on the last page | Upcoming collections first, nearest date first, then past ones, latest first |
| "Boîte à pizza" listed "Boîte de conserve" (Blainville) or the plain cardboard entry (Rosemère, at an equal score) beside the soiled-paper entry | The guide's own examples ("boîte de pizza") count as phrases, like keywords; and a runner-up that matches only some of the words the best entry matches is dropped. One that matches the same words another way (a bare "carton": clean or soiled) or another part of the question stays |

All three are covered by `SmallFixesIntegrationTest`.

## Second pass: everything, systematically

The first pass followed five people. The second went through every
combination instead, again on the local copy:

- **Data**: every sector of every town (22) for each of the next 62 days,
  checked for duplicates, unknown bin types and days with no collection
  where the calendar has one; and all 169 guide entries, each in three
  languages, checked for empty names, instructions or sources.
- **Pages**: the five routes, in each town, each language, on a desktop and
  on a phone - 120 page loads - checked for browser errors, failed calls,
  raw message keys, `undefined`/`NaN`, pages wider than the screen, and
  navigation in the wrong language.
- **Administrator forms**: every form of the console (one-off collection,
  guide card, notice, closed day, then editing the new card) submitted for
  each town - 20 saves.
- **Accounts and edits through the API**: registration and login rules, the
  same email in other case, preferences and sectors, a resident on the admin
  API, a notice and who sees it and in whose inbox, a closed day moving a
  collection and coming back, a guide edit reaching the guide and the
  assistant, and bad requests.

Two more problems, both real:

| Found | Why | Fix |
|---|---|---|
| The admin console could not add or save any guide card: every attempt was refused (400) | the API required an `itemKey` the console never sends; the console's own tests mock the API | The key is made from the English name when none is given ("scented-candle", then "scented-candle-2"); an edit keeps its key (`AdminSortingConsoleIntegrationTest`) |
| A published notice never reached any resident's inbox on the free deployment, although the settings page promises it | only the Kafka consumer filled inboxes, and production runs without a broker | Without a broker, publishing the notice fills the inboxes in the same transaction, under the event's id, so a later broker relay cannot deliver it twice (`NoticeInboxWithoutBrokerIntegrationTest`) |

After these, every sweep records no problems: 0 in the data, 0 in 120 page
loads, 20 of 20 saves, 29 of 29 API checks, 88 of 88 questions.

## Not changed

- The guide entries themselves are still written without accents
  ("Papiers et cartons souilles d aliments"), as every migration has stored
  them. Rewriting four towns' guides is a data change of its own.
- The admin collection list starts at its latest date (May 2027), so this
  week is on the last page.
- A pizza-box answer lists "Boite de conserve" as a related entry, on the
  shared word "boite".
