import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import HomeView from "./HomeView.vue";
import { useMunicipalityStore } from "../stores/municipality";
import type { Municipality } from "../api/municipalities";

const get = vi.fn();

vi.mock("../api/client", () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  },
  ApiError: class ApiError extends Error {}
}));

/**
 * When to put the bin out is the town's own rule.
 *
 * The home page told every town Blainville's hours, and said "tonight" even
 * when the next collection was days away.
 */
const town = (slug: string, hours: [string, string, string] | null): Municipality => ({
  slug,
  name: slug,
  sourceUrl: null,
  calendarVerified: true,
  guideVerified: true,
  sectors: [{ code: "all", nameFr: "Tout", nameEn: "All", nameZh: "全" }, { code: "a", nameFr: "A", nameEn: "A", nameZh: "A" }],
  hasStreetDirectory: false,
  binsOutFrom: hours?.[0] ?? null,
  binsOutBy: hours?.[1] ?? null,
  binsInBy: hours?.[2] ?? null
});

const key = (daysFromNow: number) => {
  const d = new Date(Date.now() + daysFromNow * 24 * 60 * 60 * 1000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

describe("HomeView bin hours", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    get.mockReset();
  });

  async function homeFor(municipality: Municipality, inDays: number) {
    get.mockResolvedValue([{
      id: 1, collectionDate: key(inDays), sector: "a", collectionType: "garbage", binColor: "black",
      noteFr: null, noteEn: null, noteZh: null, sourceUrl: null
    }]);
    const store = useMunicipalityStore();
    store.all = [municipality];
    store.loaded = true;
    store.select(municipality.slug);
    const wrapper = mount(HomeView);
    await flushPromises();
    return wrapper.text();
  }

  it("gives Boisbriand its own hours, tonight when the collection is tomorrow", async () => {
    const text = await homeFor(town("boisbriand", ["18:00", "07:00", "22:00"]), 1);
    expect(text).toContain("Sortez le bac apres 18 h ce soir et avant 7 h demain matin.");
    expect(text).not.toContain("20 h");
  });

  it("says the evening before, not tonight, when the collection is days away", async () => {
    const text = await homeFor(town("blainville", ["20:00", "06:00", "20:00"]), 4);
    expect(text).toContain("Sortez le bac apres 20 h la veille");
    expect(text).not.toContain("ce soir");
  });

  it("asks for the bin back by the town's hour on collection day", async () => {
    const text = await homeFor(town("boisbriand", ["18:00", "07:00", "22:00"]), 0);
    expect(text).toContain("Rentrez le bac vide avant 22 h ce soir.");
  });

  it("says nothing about hours for a town that prints none", async () => {
    const text = await homeFor(town("rosemere", null), 1);
    expect(text).not.toMatch(/Sortez le bac|Rentrez le bac/);
  });
});
