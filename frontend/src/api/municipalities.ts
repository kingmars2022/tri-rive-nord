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
};

export function fetchMunicipalities(): Promise<Municipality[]> {
  return api.get<Municipality[]>("/municipalities");
}
