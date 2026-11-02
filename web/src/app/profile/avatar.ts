/** Image source for an avatar value: a Google picture URL as stored, or a bundled preset. */
export function avatarSrc(avatar: string): string {
  return avatar.startsWith("https://") ? avatar : `/avatars/${avatar}.svg`;
}
