import { api } from "./client";
import { usePreferenceStore } from "../stores/preferences";
import type { Language } from "../i18n/messages";

export interface AssistantSource {
  itemId: number;
  name: string;
  destinationType: string | null;
  binColor: string | null;
  sourceUrl: string | null;
  score: number;
}

export interface AssistantAnswer {
  answer: string;
  /** false when the guide had nothing relevant - the answer is then an explicit "I don't know". */
  grounded: boolean;
  /** "template" or "anthropic:<model>", so the UI never has to guess what answered. */
  provider: string;
  sources: AssistantSource[];
}

export function askAssistant(question: string, language: Language) {
  // In the body rather than the query string, so this one does not come from
  // the client's blanket parameter: the server reads it off the request
  // object and validates it there with the question.
  const { municipality } = usePreferenceStore();
  return api.post<AssistantAnswer>("/assistant/ask", { question, language, municipality });
}
