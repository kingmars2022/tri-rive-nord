import { defineStore } from "pinia";
import { fetchMunicipalities, type Municipality } from "../api/municipalities";
import { usePreferenceStore } from "./preferences";

type State = {
  all: Municipality[];
  loaded: boolean;
};

export const useMunicipalityStore = defineStore("municipalities", {
  state: (): State => ({ all: [], loaded: false }),

  getters: {
    /**
     * The city whose data every page is showing.
     *
     * <p>Falls back to the first city rather than to a hard-coded slug: the
     * stored preference can name a city that no longer exists, and a page
     * that quietly showed Blainville's bins to someone who had chosen
     * Rosemere is the failure this whole layer is for.
     */
    current(state): Municipality | null {
      if (!state.all.length) return null;
      const preferences = usePreferenceStore();
      return state.all.find((m) => m.slug === preferences.municipality) ?? state.all[0];
    },

    sectors(): MunicipalitySectorList {
      return this.current?.sectors ?? [];
    }
  },

  actions: {
    async load() {
      if (this.loaded) return;
      this.all = await fetchMunicipalities();
      this.loaded = true;

      const preferences = usePreferenceStore();
      const chosen = this.current;
      if (chosen && chosen.slug !== preferences.municipality) {
        preferences.setMunicipality(chosen.slug);
      }
      this.ensureValidSector();
    },

    select(slug: string) {
      const preferences = usePreferenceStore();
      preferences.setMunicipality(slug);
      this.ensureValidSector();
    },

    /**
     * Keeps the chosen sector one this city actually has.
     *
     * <p>Sectors are per city, so "north" means nothing in a city that does
     * not split at boulevard de la Seigneurie. Without this, switching city
     * would send a sector the server rejects with a 404, and the page would
     * show an error for what is really a stale preference.
     */
    ensureValidSector() {
      const preferences = usePreferenceStore();
      const sectors = this.current?.sectors ?? [];
      if (!sectors.length) {
        // A city with no sectors on file has not been loaded yet. Leaving the
        // previous city's sector in place sent "north" to a city that has no
        // north, and the 404 that came back was shown to the resident as an
        // error. There is nothing valid to choose, so choose nothing.
        preferences.setSector("");
        return;
      }
      if (!sectors.some((s) => s.code === preferences.sector)) {
        // Never "all" when the city has anything more specific. Every city
        // lists "all" first, and a resident left on it matches only
        // city-wide collections: in Sainte-Therese that hid every garbage and
        // recycling day, in Boisbriand everything. The server's
        // defaultSectorOf already skips it for new accounts; switching city
        // here did not.
        const specific = sectors.find((s) => s.code !== "all");
        preferences.setSector((specific ?? sectors[0]).code);
      }
    }
  }
});

type MunicipalitySectorList = Municipality["sectors"];
