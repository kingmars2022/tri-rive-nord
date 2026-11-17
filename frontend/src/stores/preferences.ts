import { defineStore } from "pinia";
import type { Language } from "../i18n/messages";

/**
 * A sector code, not a fixed set.
 *
 * <p>This was `"north" | "south"`, which is Blainville's two halves of
 * boulevard de la Seigneurie. Sectors belong to a city now, so the valid
 * values arrive with the city rather than being known at compile time; the
 * municipality store is what keeps this in step with the city on screen.
 */
export type Sector = string;

type PreferenceState = {
  language: Language;
  /** Slug of the city every page shows. */
  municipality: string;
  sector: Sector;
  remindersEnabled: boolean;
};

const storageKey = "tri-rive-nord.preferences";

function loadPreferences(): PreferenceState {
  const fallback: PreferenceState = {
    language: "fr",
    municipality: "blainville",
    sector: "north",
    remindersEnabled: true
  };

  const raw = localStorage.getItem(storageKey);
  if (!raw) {
    return fallback;
  }

  try {
    return { ...fallback, ...JSON.parse(raw) };
  } catch {
    // Anything can end up in localStorage: a half-written value, a key reused
    // by something else, an extension. This runs while the first view is being
    // created, so an unguarded parse took the whole application down at
    // startup - for a stored language setting. The auth store already guarded
    // against it; this one did not.
    return fallback;
  }
}

export const usePreferenceStore = defineStore("preferences", {
  state: (): PreferenceState => loadPreferences(),
  actions: {
    save() {
      localStorage.setItem(storageKey, JSON.stringify(this.$state));
    },
    setLanguage(language: Language) {
      this.language = language;
      this.save();
    },
    setMunicipality(municipality: string) {
      this.municipality = municipality;
      this.save();
    },
    setSector(sector: Sector) {
      this.sector = sector;
      this.save();
    },
    setRemindersEnabled(enabled: boolean) {
      this.remindersEnabled = enabled;
      this.save();
    }
  }
});

