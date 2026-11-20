import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import AdminView from "./AdminView.vue";
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

vi.mock("../api/agent", () => ({
  planWithAgent: vi.fn(),
  executeAgentPlan: vi.fn()
}));

/**
 * The console's sector pickers come from the selected city.
 *
 * They were hard-coded to Blainville's north/south/all, so in any other city
 * an administrator could only save collections no resident would ever see.
 */
const sector = (code: string, fr: string, en: string) => ({ code, nameFr: fr, nameEn: en, nameZh: fr });

const city = (slug: string, name: string, sectors: Municipality["sectors"]): Municipality => ({
  slug,
  name,
  sourceUrl: null,
  calendarVerified: true,
  guideVerified: true,
  sectors,
  hasStreetDirectory: false,
  binsOutFrom: null,
  binsOutBy: null,
  binsInBy: null
});

const blainville = city("blainville", "Blainville", [
  sector("all", "Tout le territoire", "Whole city"),
  sector("south", "Au sud du boulevard", "South"),
  sector("north", "Au nord du boulevard", "North")
]);

const sainteTherese = city("sainte-therese", "Sainte-Therese", [
  sector("all", "Tout le territoire", "Whole city"),
  sector("a", "Zone A", "Zone A"),
  sector("b", "Zone B", "Zone B")
]);

describe("AdminView sectors", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    get.mockReset();
    get.mockImplementation(async (path: string) => {
      if (path.startsWith("/admin/collections")) {
        return {
          items: [{ id: 1, collectionDate: "2026-12-01", sector: "a", collectionType: "recycling", binColor: "blue" }],
          page: 0,
          size: 25,
          total: 1
        };
      }
      return [];
    });
  });

  async function mountIn(selected: Municipality) {
    const municipalities = useMunicipalityStore();
    municipalities.all = [blainville, sainteTherese];
    municipalities.loaded = true;
    municipalities.select(selected.slug);
    const wrapper = mount(AdminView);
    await flushPromises();
    return wrapper;
  }

  function optionsOf(wrapper: Awaited<ReturnType<typeof mountIn>>) {
    return wrapper
      .findAll("select")
      .filter((s) => s.findAll("option").some((o) => o.attributes("value") === "all"))
      .map((s) => s.findAll("option").map((o) => o.attributes("value")));
  }

  it("offers the selected city's sectors in both pickers, not Blainville's", async () => {
    const wrapper = await mountIn(sainteTherese);

    const pickers = optionsOf(wrapper);
    expect(pickers).toHaveLength(2); // a collection, and a closed day
    for (const values of pickers) {
      expect(values).toEqual(["all", "a", "b"]);
    }
    expect(wrapper.html()).not.toContain('value="north"');
  });

  it("names sectors in the schedule table instead of showing their codes", async () => {
    const wrapper = await mountIn(sainteTherese);

    const row = wrapper.findAll("tr").find((r) => r.text().includes("2026-12-01"));
    expect(row?.text()).toContain("Zone A");
  });

  it("reloads the city's lists when the city changes", async () => {
    await mountIn(blainville);
    get.mockClear();

    useMunicipalityStore().select("sainte-therese");
    await flushPromises();

    const paths = get.mock.calls.map((call) => call[0] as string);
    expect(paths).toContain("/admin/collection-holidays");
    expect(paths.some((p) => p.startsWith("/admin/collections"))).toBe(true);
    expect(paths).toContain("/admin/sorting-items");
    expect(paths).toContain("/admin/notices");
  });
});
