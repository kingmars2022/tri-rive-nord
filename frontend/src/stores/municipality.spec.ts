import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useMunicipalityStore } from "./municipality";
import { usePreferenceStore } from "./preferences";
import type { Municipality } from "../api/municipalities";

const BLAINVILLE: Municipality = {
  slug: "blainville",
  name: "Blainville",
  sourceUrl: "https://blainville.ca/collectes",
  calendarVerified: true,
  guideVerified: true,
  sectors: [
    { code: "all", nameFr: "Tout le territoire", nameEn: "Whole city", nameZh: "全市" },
    { code: "north", nameFr: "Nord", nameEn: "North", nameZh: "北" }
  ],
  hasStreetDirectory: false
};

/** Offered, but nothing read from its own calendar yet, so no sectors either. */
const ROSEMERE: Municipality = {
  slug: "rosemere",
  name: "Rosemere",
  sourceUrl: null,
  calendarVerified: false,
  guideVerified: false,
  sectors: [],
  hasStreetDirectory: false
};

describe("municipality store", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("keeps a sector the chosen city actually has, and never falls back to the wildcard", () => {
    const store = useMunicipalityStore();
    const preferences = usePreferenceStore();
    store.all = [BLAINVILLE];
    store.loaded = true;

    preferences.setSector("south"); // Blainville has no south in this fixture
    store.ensureValidSector();

    // Not "all", although it is listed first: a resident on "all" sees only
    // city-wide collections. This test used to expect "all", which is how the
    // defect passed review - switching to Sainte-Therese or Boisbriand left
    // residents without their garbage and recycling days.
    expect(preferences.sector).toBe("north");
  });

  it("uses the wildcard only when the city has nothing more specific", () => {
    const store = useMunicipalityStore();
    const preferences = usePreferenceStore();
    store.all = [{ ...BLAINVILLE, sectors: [BLAINVILLE.sectors[0]!] }];
    store.loaded = true;

    preferences.setSector("south");
    store.ensureValidSector();

    expect(preferences.sector).toBe("all");
  });

  /**
   * The regression this file exists for.
   *
   * Switching to a city with no sectors used to leave the previous city's
   * sector in place, so the page asked Rosemere for its northern sector and
   * put the server's 404 on screen -- in English, naming a sector, for what
   * is not an error at all. There is nothing valid to choose here, so the
   * right answer is to choose nothing.
   */
  it("clears the sector when the chosen city has none loaded", () => {
    const store = useMunicipalityStore();
    const preferences = usePreferenceStore();
    store.all = [BLAINVILLE, ROSEMERE];
    store.loaded = true;

    preferences.setSector("north");
    store.select("rosemere");

    expect(preferences.municipality).toBe("rosemere");
    expect(preferences.sector).toBe("");
  });

  it("falls back to the first city when the stored one is gone", () => {
    const preferences = usePreferenceStore();
    preferences.setMunicipality("a-city-that-was-removed");

    const store = useMunicipalityStore();
    store.all = [BLAINVILLE, ROSEMERE];
    store.loaded = true;

    expect(store.current?.slug).toBe("blainville");
  });
});
