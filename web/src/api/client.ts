import type { components } from "./generated";

export type Profile = components["schemas"]["ProfileDto"];
export type ProfileUpdate = components["schemas"]["UpdateProfileRequest"];
export type AvatarPresets = components["schemas"]["AvatarPresetsDto"];

export type Room = components["schemas"]["RoomDto"];
export type RoomSummary = components["schemas"]["RoomSummaryDto"];
export type RoomConfig = components["schemas"]["RoomConfigDto"];
export type RoomState = Room["state"];
export type PackChoice = components["schemas"]["PackChoiceDto"];
export type CreateRoom = components["schemas"]["CreateRoomRequest"];
export type Invite = components["schemas"]["InviteDto"];
export type Member = components["schemas"]["MemberDto"];
export type Franchise = components["schemas"]["FranchiseDto"];
export type FranchiseInput = components["schemas"]["FranchiseRequest"];
export type Transition = components["schemas"]["TransitionDto"];
export type Handover = components["schemas"]["HandoverDto"];
export type JoinPreview = components["schemas"]["JoinPreviewDto"];
export type JoinResult = components["schemas"]["JoinResultDto"];
export type Pool = components["schemas"]["PoolDto"];
export type PoolPlayer = components["schemas"]["PoolPlayerDto"];
export type PoolSet = components["schemas"]["PoolSetDto"];
export type PoolPlayerInput = components["schemas"]["PoolPlayerRequest"];
export type DatasetPack = components["schemas"]["DatasetPackDto"];
export type DatasetSummary = components["schemas"]["DatasetSummaryDto"];
export type ImportReport = components["schemas"]["ImportReportDto"];

/** A non-2xx answer from the server. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
    /** Seconds from a Retry-After header (429), when the server sent one. */
    readonly retryAfter: number | null = null,
  ) {
    super(`Request failed with status ${status}`);
  }
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  // A FormData body sets its own multipart Content-Type (with the boundary).
  const headers = init.body && !(init.body instanceof FormData) ? { "Content-Type": "application/json" } : undefined;
  const response = await fetch(path, { credentials: "same-origin", headers, ...init });
  if (!response.ok) {
    const retry = Number(response.headers?.get?.("Retry-After"));
    throw new ApiError(
      response.status,
      await response.json().catch(() => null),
      Number.isFinite(retry) && retry > 0 ? retry : null,
    );
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

const room = (id: string) => `/api/rooms/${encodeURIComponent(id)}`;

/** The caller's rooms for the home hub, most recently changed first. */
export async function listMyRooms(): Promise<RoomSummary[]> {
  return (await request("/api/rooms")).json();
}

/** The packs a new room can start from, each with its presets. */
export async function listPacks(): Promise<PackChoice[]> {
  return (await request("/api/rooms/packs")).json();
}

export async function createRoom(input: CreateRoom): Promise<Room> {
  return (await send("POST", "/api/rooms", input)).json();
}

export async function getRoom(id: string): Promise<Room> {
  return (await request(room(id))).json();
}

/** Saves the whole config. `version` is the config version the editor started from. */
export async function saveConfig(id: string, version: number, config: RoomConfig): Promise<Room> {
  return (await send("PUT", `${room(id)}/config`, { version, config })).json();
}

/** The active invite, or null when there is none (never issued, revoked or expired). */
export async function getInvite(id: string): Promise<Invite | null> {
  try {
    return await (await request(`${room(id)}/invite`)).json();
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** Issues a new code; an active one is replaced. */
export async function issueInvite(id: string): Promise<Invite> {
  return (await send("POST", `${room(id)}/invite`, {})).json();
}

export async function revokeInvite(id: string): Promise<void> {
  await send("DELETE", `${room(id)}/invite`);
}

export async function getMembers(id: string): Promise<Member[]> {
  return (await request(`${room(id)}/members`)).json();
}

/** The Commissioner's own franchise. */
export async function createOwnFranchise(id: string, input: FranchiseInput): Promise<Franchise> {
  return (await send("POST", `${room(id)}/franchises`, input)).json();
}

export async function editFranchise(
  id: string,
  franchiseId: string,
  input: FranchiseInput,
): Promise<Franchise> {
  return (await send("PATCH", `${room(id)}/franchises/${encodeURIComponent(franchiseId)}`, input)).json();
}

/** Removes a manager; answers with the members who remain. */
export async function removeFranchise(id: string, franchiseId: string): Promise<Member[]> {
  return (await send("DELETE", `${room(id)}/franchises/${encodeURIComponent(franchiseId)}`)).json();
}

export async function handOver(id: string, franchiseId: string): Promise<Handover> {
  return (await send("POST", `${room(id)}/commissioner`, { franchiseId })).json();
}

export async function advanceRoom(id: string, to: RoomState): Promise<Transition> {
  return (await send("POST", `${room(id)}/transitions`, { to })).json();
}

export async function getTransitions(id: string): Promise<Transition[]> {
  return (await request(`${room(id)}/transitions`)).json();
}

export async function previewJoin(code: string): Promise<JoinPreview> {
  return (await send("POST", "/api/join/preview", { code })).json();
}

export async function joinRoom(code: string, franchise: FranchiseInput): Promise<JoinResult> {
  return (await send("POST", "/api/join", { code, franchise })).json();
}

/** A room's player pool. `hasPool` is false until the room has a copy of the season data. */
export async function getPool(id: string): Promise<Pool> {
  return (await request(`${room(id)}/pool`)).json();
}

export async function addPoolPlayer(id: string, input: PoolPlayerInput): Promise<Pool> {
  return (await send("POST", `${room(id)}/pool/players`, input)).json();
}

export async function editPoolPlayer(id: string, playerId: string, input: PoolPlayerInput): Promise<Pool> {
  return (await send("PUT", `${room(id)}/pool/players/${encodeURIComponent(playerId)}`, input)).json();
}

export async function removePoolPlayer(id: string, playerId: string, version: number): Promise<Pool> {
  return (await send("DELETE", `${room(id)}/pool/players/${encodeURIComponent(playerId)}?version=${version}`)).json();
}

/** Replaces the pool and set order with the latest season data. Version 0 when the room has no copy. */
export async function resetPool(id: string, version: number): Promise<Pool> {
  return (await send("POST", `${room(id)}/pool/reset`, { version })).json();
}

/** Saves the whole set order; `version` is the pool version the screen read. */
export async function saveSetOrder(id: string, version: number, order: string[]): Promise<Pool> {
  return (await send("PUT", `${room(id)}/pool/sets`, { version, order })).json();
}

/** Packs an app admin can import season data for (403 when the caller is not an admin). */
export async function listDatasetPacks(): Promise<DatasetPack[]> {
  return (await request("/api/admin/datasets/packs")).json();
}

/** Every season data version of a pack, newest first. */
export async function listDatasets(packId: string): Promise<DatasetSummary[]> {
  return (await request(`/api/admin/datasets?packId=${encodeURIComponent(packId)}`)).json();
}

/**
 * Imports a CSV or JSON file. Resolves with the report when a version was made; a file with no
 * valid row rejects with an ApiError (422) whose body carries `report`.
 */
export async function importDataset(packId: string, file: File): Promise<ImportReport> {
  const form = new FormData();
  form.append("packId", packId);
  form.append("file", file);
  return (await request("/api/admin/datasets", { method: "POST", body: form })).json();
}
