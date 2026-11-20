import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import SettingsView from "./SettingsView.vue";
import { useMunicipalityStore } from "../stores/municipality";
import { usePreferenceStore } from "../stores/preferences";
import type { Municipality } from "../api/municipalities";

const get = vi.fn();
const put = vi.fn();

vi.mock("../api/client", () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    put: (...args: unknown[]) => put(...args),
    post: vi.fn(),
    delete: vi.fn()
  },
  ApiError: class ApiError extends Error {}
}));

/**
 * Finding a sector by street, for cities that assign sectors street by
 * street. What matters is the last step: choosing a street has to change the
 * sector the home page reads, or the lookup is a list that does nothing.
 */
const sector = (code: string, fr: string) => ({ code, nameFr: fr, nameEn: fr, nameZh: fr });

const boisbriand: Municipality = {
  slug: "boisbriand",
  name: "Boisbriand",
  sourceUrl: null,
  calendarVerified: true,
  guideVerified: false,
  sectors: [
    sector("all", "Tout le territoire"),
    sector("a-mardi", "Secteur A, mardi"),
    sector("a-vendredi", "Secteur A, vendredi"),
    sector("b-vendredi", "Secteur B, vendredi")
  ],
  hasStreetDirectory: true,
  binsOutFrom: null,
  binsOutBy: null,
  binsInBy: null
};

const blainville: Municipality = {
  ...boisbriand,
  slug: "blainville",
  name: "Blainville",
  sectors: [sector("all", "Tout le territoire"), sector("north", "Nord"), sector("south", "Sud")],
  hasStreetDirectory: false,
  binsOutFrom: null,
  binsOutBy: null,
  binsInBy: null
};

function mountFor(city: Municipality) {
  const municipalities = useMunicipalityStore();
  municipalities.all = [city];
  municipalities.loaded = true;
  usePreferenceStore().setMunicipality(city.slug);
  return mount(SettingsView);
}

describe("SettingsView street lookup", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    get.mockReset();
    put.mockReset();
    vi.useFakeTimers();
  });

  it("is not offered where sectors are not assigned by street", () => {
    const wrapper = mountFor(blainville);
    expect(wrapper.find(".street-lookup").exists()).toBe(false);
  });

  it("finds a street and makes its sector the resident's", async () => {
    get.mockResolvedValue([
      { name: "Grande-Allée (nos 3153 à 3245)", sectorCode: "a-vendredi" },
      { name: "Grande-Allée (nos 505 à 1700)", sectorCode: "a-mardi" }
    ]);
    const wrapper = mountFor(boisbriand);

    await wrapper.find(".street-lookup input").setValue("grande allee");
    vi.advanceTimersByTime(300);
    await flushPromises();

    expect(get).toHaveBeenCalledWith("/municipalities/boisbriand/streets?q=grande%20allee");
    const options = wrapper.findAll(".street-results button");
    expect(options.map((o) => o.text())).toEqual([
      "Grande-Allée (nos 3153 à 3245) — Secteur A, vendredi",
      "Grande-Allée (nos 505 à 1700) — Secteur A, mardi"
    ]);

    await options[1]!.trigger("click");

    expect(usePreferenceStore().sector).toBe("a-mardi");
    expect(wrapper.find(".street-chosen").text()).toContain("Secteur A, mardi");
    expect(wrapper.find(".street-results").exists()).toBe(false);
  });

  it("waits for two letters, and only the newest answer is shown", async () => {
    let answerFirst: (value: unknown) => void = () => {};
    get
      .mockImplementationOnce(() => new Promise((resolve) => { answerFirst = resolve; }))
      .mockResolvedValueOnce([{ name: "Grande-Côte (nos 21 à 120)", sectorCode: "b-vendredi" }]);
    const wrapper = mountFor(boisbriand);
    const input = wrapper.find(".street-lookup input");

    await input.setValue("g");
    vi.advanceTimersByTime(300);
    expect(get).not.toHaveBeenCalled();

    await input.setValue("gr");
    vi.advanceTimersByTime(300);
    await input.setValue("grande-cote");
    vi.advanceTimersByTime(300);
    await flushPromises();

    // The slow answer to "gr" arrives last; it must not replace the list.
    answerFirst([{ name: "Gravel", sectorCode: "a-mardi" }]);
    await flushPromises();

    expect(wrapper.findAll(".street-results button").map((o) => o.text())).toEqual([
      "Grande-Côte (nos 21 à 120) — Secteur B, vendredi"
    ]);
  });

  it("says so when nothing matches, and says where the list comes from", async () => {
    get.mockResolvedValue([]);
    const wrapper = mountFor(boisbriand);

    await wrapper.find(".street-lookup input").setValue("zzz");
    vi.advanceTimersByTime(300);
    await flushPromises();

    expect(wrapper.text()).toContain("Aucune rue trouvee");
    expect(wrapper.text()).toContain("calendrier 2026");
  });
});
