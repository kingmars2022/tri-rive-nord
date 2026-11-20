import { api } from "./client";

export type MunicipalitySector = {
  code: string;
  nameFr: string;
  nameEn: string;
  nameZh: string;
};

export type Municipality = {
  slug: string;
  name: string;
  sourceUrl: string | null;
  /**
   * Whether this city's collection calendar has been read from its own
   * published document yet. The switcher offers a city either way, because
   * hiding the ones with no data would leave a resident unable to tell
   * "we do not cover your city" from "we have not loaded it yet".
   */
  calendarVerified: boolean;
  guideVerified: boolean;
  sectors: MunicipalitySector[];
  /**
   * True where residents find their sector by street rather than by name -
   * Boisbriand, which sets the collection weekday street by street.
   */
  hasStreetDirectory: boolean;
  /**
   * The town's own curbside rule, as "HH:mm": out from the evening before,
   * out by the morning of, back in by. Null where the town prints none, and
   * then the home page says nothing rather than another town's hours.
   */
  binsOutFrom: string | null;
  binsOutBy: string | null;
  binsInBy: string | null;
};

/** A street, or one civic-number range of it, and the sector it is in. */
export type Street = {
  name: string;
  sectorCode: string;
};

export function fetchMunicipalities(): Promise<Municipality[]> {
  return api.get<Municipality[]>("/municipalities");
}

export function fetchStreets(slug: string, query: string): Promise<Street[]> {
  return api.get<Street[]>(
    `/municipalities/${encodeURIComponent(slug)}/streets?q=${encodeURIComponent(query)}`
  );
}
