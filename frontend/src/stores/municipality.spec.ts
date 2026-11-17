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
  ]
};

/** Offered, but nothing read from its own calendar yet, so no sectors either. */
const ROSEMERE: Municipality = {
  slug: "rosemere",
  name: "Rosemere",
  sourceUrl: null,
  calendarVerified: false,
  guideVerified: false,
  sectors: []
};

describe("municipality store", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("keeps a sector the chosen city actually has", () => {
    const store = useMunicipalityStore();
    const preferences = usePreferenceStore();
    store.all = [BLAINVILLE];
    store.loaded = true;

    preferences.setSector("south"); // Blainville has no south in this fixture
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
