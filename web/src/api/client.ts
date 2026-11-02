import type { components } from "./generated";

export type Profile = components["schemas"]["ProfileDto"];
export type ProfileUpdate = components["schemas"]["UpdateProfileRequest"];
export type AvatarPresets = components["schemas"]["AvatarPresetsDto"];

/** A non-2xx answer from the server. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`Request failed with status ${status}`);
  }
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = init.body ? { "Content-Type": "application/json" } : undefined;
  const response = await fetch(path, { credentials: "same-origin", headers, ...init });
  if (!response.ok) {
    throw new ApiError(response.status, await response.json().catch(() => null));
  }
  return response;
}

function send(method: string, path: string, body?: unknown): Promise<Response> {
  return request(path, { method, body: body === undefined ? undefined : JSON.stringify(body) });
}

/** The signed-in profile, or null when there is no valid session. */
export async function getMe(): Promise<Profile | null> {
  try {
    return (await request("/api/me")).json();
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}

export async function updateProfile(update: ProfileUpdate): Promise<Profile> {
  return (await send("PATCH", "/api/me", update)).json();
}

export async function getAvatars(): Promise<AvatarPresets> {
  return (await request("/api/avatars")).json();
}

export async function startEmail(email: string): Promise<void> {
  await send("POST", "/api/auth/email/start", { email });
}

export async function verifyCode(email: string, code: string): Promise<Profile> {
  return (await send("POST", "/api/auth/email/verify", { email, code })).json();
}

export async function verifyLink(token: string): Promise<Profile> {
  return (await send("POST", "/api/auth/email/verify", { token })).json();
}

export async function signOut(): Promise<void> {
  await send("POST", "/api/auth/sign-out");
}

/** Where the Google button points; the server handles the whole OAuth flow. */
export const GOOGLE_SIGN_IN_URL = "/api/oauth2/authorization/google";
