import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import AdminView from "./AdminView.vue";
import { usePreferenceStore } from "../stores/preferences";

const get = vi.fn();
const post = vi.fn();
const put = vi.fn();
const del = vi.fn();

vi.mock("../api/client", () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args),
    delete: (...args: unknown[]) => del(...args)
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  }
}));

vi.mock("../api/agent", () => ({
  planWithAgent: vi.fn(),
  executeAgentPlan: vi.fn()
}));

/**
 * A notice belongs to one city and reaches only that city's residents, so the
 * notice panel has to say which city it is writing to, and has to follow the
 * header's city switch rather than keep the previous city's list on screen.
 */
describe("AdminView notices", () => {
  const notice = {
    id: 5,
    municipalityId: 1,
    startsOn: "2027-01-02",
    endsOn: "2027-01-03",
    titleFr: "Collecte reportee",
    titleEn: "Collection postponed",
    titleZh: "收集推迟",
    bodyFr: "Corps",
    bodyEn: "Body",
    bodyZh: "正文",
    sourceUrl: null,
    active: true
  };

  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
    get.mockImplementation(async (path: string) => {
      if (path.startsWith("/admin/notices")) return [notice];
      if (path.startsWith("/admin/collections")) {
        return { items: [], page: 0, size: 25, total: 0 };
      }
      return [];
    });
    post.mockResolvedValue({});
    put.mockResolvedValue({});
    del.mockResolvedValue(undefined);
  });

  async function mountAdmin() {
    const wrapper = mount(AdminView);
    await flushPromises();
    const found = wrapper
      .findAll(".admin-panel")
      .find((section) => section.find("h2").text() === "Avis speciaux");
    if (!found) throw new Error("no notice panel");
    return { wrapper, panel: found };
  }

  const noticeCalls = () =>
    get.mock.calls.map((call) => String(call[0])).filter((path) => path.startsWith("/admin/notices"));

  it("lists, publishes, edits and deletes in the city being administered", async () => {
    usePreferenceStore().setMunicipality("sainte-therese");
    const { panel } = await mountAdmin();

    expect(noticeCalls()).toEqual(["/admin/notices?municipality=sainte-therese"]);

    await panel.find('input[type="date"]').setValue("2027-01-02");
    await panel.find("form").trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/admin/notices?municipality=sainte-therese", expect.anything());

    const edit = panel.findAll("tbody button").find((button) => button.text() === "Modifier")!;
    await edit.trigger("click");
    await panel.find("form").trigger("submit");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/admin/notices/5?municipality=sainte-therese", expect.anything());

    const remove = panel.findAll("tbody button").find((button) => button.text() === "Supprimer")!;
    await remove.trigger("click");
    await flushPromises();
    expect(del).toHaveBeenCalledWith("/admin/notices/5?municipality=sainte-therese");
  });

  it("reloads the list and drops an open edit when the city changes", async () => {
    const { panel } = await mountAdmin();
    const edit = panel.findAll("tbody button").find((button) => button.text() === "Modifier")!;
    await edit.trigger("click");
    await flushPromises();
    expect(panel.text()).toContain("#5");

    usePreferenceStore().setMunicipality("rosemere");
    await flushPromises();

    expect(noticeCalls()).toContain("/admin/notices?municipality=rosemere");
    // The open edit belonged to Blainville's notice 5; saving it now would be
    // sent as Rosemere's, so the form goes back to creating.
    expect(panel.text()).not.toContain("#5");
  });

  it("names the city a notice will be published to", async () => {
    usePreferenceStore().setMunicipality("boisbriand");
    const { panel } = await mountAdmin();

    expect(panel.find(".notice-city").text()).toContain("boisbriand");
  });
});
