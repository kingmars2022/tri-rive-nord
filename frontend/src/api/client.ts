import { useAuthStore } from "../stores/auth";
import { usePreferenceStore } from "../stores/preferences";

/**
 * Adds the chosen city to every request that does not already name one.
 *
 * <p>Done here rather than at each call site on purpose. There are more than
 * twenty of them, and the cost of forgetting one is not a broken page -- it
 * is a page that works and shows another city's rules, which is the one
 * failure this whole layer exists to prevent. A seam every request already
 * passes through cannot be forgotten.
 *
 * <p>Endpoints that have nothing to do with a city, such as signing in, get
 * the parameter too and ignore it. That is the price of the guarantee, and a
 * cheaper one than an allowlist nobody updates.
 */
function withMunicipality(path: string): string {
  if (path.includes("municipality=")) {
    return path;
  }
  const { municipality } = usePreferenceStore();
  if (!municipality) {
    return path;
  }
  return path + (path.includes("?") ? "&" : "?") + "municipality=" + encodeURIComponent(municipality);
}

export class ApiError extends Error {
  status: number;
  /** Seconds the server asked us to wait, when it said to retry at all. */
  retryAfter: number | null;

  constructor(status: number, message: string, retryAfter: number | null = null) {
    super(message);
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const auth = useAuthStore();
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");

  if (auth.token) {
    headers.set("Authorization", `Bearer ${auth.token}`);
  }

  const response = await fetch(`/api${withMunicipality(path)}`, { ...options, headers });

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 401) {
      auth.logout();
    }
    const message = body?.message ?? `Request failed with status ${response.status}`;
    const retryAfter = Number(response.headers.get("Retry-After"));
    throw new ApiError(response.status, message, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null);
  }

  return body as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: "POST", body: data === undefined ? undefined : JSON.stringify(data) }),
  put: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: "PUT", body: data === undefined ? undefined : JSON.stringify(data) }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" })
};
