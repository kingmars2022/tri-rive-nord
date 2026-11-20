import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import SortingView from "./SortingView.vue";
import type { SortingItem } from "../api/sortingGuide";

const guide = vi.fn();

vi.mock("../api/client", () => ({
  api: {
    get: (...args: unknown[]) => guide(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  },
  ApiError: class ApiError extends Error {}
}));

/**
 * A bin is named by the colour the town paints it.
 *
 * Rosemere's garbage bin is grey. The sorting page used to name every garbage
 * entry "Bac noir" whatever the town, because the label came from the
 * destination alone.
 */
const card = (id: number, destinationType: SortingItem["destinationType"], binColor: SortingItem["binColor"],
              name: string): SortingItem => ({
  id,
  destinationType,
  binColor,
  sourceUrl: null,
  fr: { name, instruction: "", location: null, availability: null, examples: [] },
  en: { name, instruction: "", location: null, availability: null, examples: [] },
  zh: { name, instruction: "", location: null, availability: null, examples: [] },
  keywordsFr: [],
  keywordsEn: [],
  keywordsZh: []
});

describe("SortingView bin labels", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    guide.mockReset();
  });

  async function mountWith(items: SortingItem[]) {
    guide.mockResolvedValue(items);
    const wrapper = mount(SortingView);
    await flushPromises();
    return wrapper;
  }

  it("names a grey garbage bin grey, on the card and on the filter", async () => {
    const wrapper = await mountWith([card(1, "garbage", "grey", "Poterie"), card(2, "recycling", "blue", "Carton")]);

    const tags = wrapper.findAll(".sorting-card .tag").map((tag) => tag.text());
    expect(tags).toContain("Bac gris");
    expect(tags).toContain("Bac bleu");
    expect(wrapper.text()).not.toContain("Bac noir");

    const filters = wrapper.findAll(".segmented button").map((button) => button.text());
    expect(filters).toContain("Bac gris");
  });

  it("still names a black garbage bin black", async () => {
    const wrapper = await mountWith([card(1, "garbage", "black", "Couches")]);

    expect(wrapper.findAll(".sorting-card .tag").map((tag) => tag.text())).toEqual(["Bac noir"]);
  });
});
