import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import SortingView from "./SortingView.vue";

const get = vi.fn();
const post = vi.fn();

vi.mock("../api/client", () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: vi.fn(),
    delete: vi.fn()
  },
  ApiError: class ApiError extends Error {}
}));

/**
 * What a resident saw on the sorting page with the free deployment:
 * a photo button that could only fail (no AWS behind it), the sources of an
 * answer run together into one name, and "Reponse generee par template".
 */
describe("SortingView photo button and answer", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    get.mockReset();
    post.mockReset();
  });

  async function mountWithPhotos(enabled: boolean) {
    get.mockImplementation(async (path: string) => (path === "/photos/status" ? { enabled } : []));
    const wrapper = mount(SortingView);
    await flushPromises();
    return wrapper;
  }

  it("offers no photo button where the deployment cannot identify photos", async () => {
    const wrapper = await mountWithPhotos(false);
    expect(wrapper.find(".photo-ask").exists()).toBe(false);
  });

  it("offers it where it can", async () => {
    const wrapper = await mountWithPhotos(true);
    expect(wrapper.find(".photo-ask").exists()).toBe(true);
  });

  it("lists each source as its own link and says the answer is the guide's own words", async () => {
    const wrapper = await mountWithPhotos(false);
    post.mockResolvedValue({
      answer: "D'après le guide de tri de Blainville : ...",
      grounded: true,
      provider: "template",
      sources: [
        { itemId: 1, name: "Papiers souilles", destinationType: "organic", binColor: "brown", sourceUrl: "https://a", score: 5 },
        { itemId: 2, name: "Boite de conserve", destinationType: "recycling", binColor: "blue", sourceUrl: "https://b", score: 3 }
      ]
    });

    await wrapper.find(".assistant-form input").setValue("pizza");
    await wrapper.find(".assistant-form").trigger("submit");
    await flushPromises();

    expect(wrapper.findAll(".assistant-sources a").map((a) => a.text())).toEqual(["Papiers souilles", "Boite de conserve"]);
    expect(wrapper.text()).toContain("Réponse tirée directement du guide de tri.");
    expect(wrapper.text()).not.toContain("template");
  });
});
