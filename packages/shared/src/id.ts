const ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

function randomChars(length: number): string {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  let out = "";
  for (const byte of bytes) {
    const index = byte % ALPHABET.length;
    const char = ALPHABET.charAt(index);
    out += char;
  }
  return out;
}

export function createClientOrderNo(userId: string): string {
  return `C${userId}-${Date.now().toString(36).toUpperCase()}-${randomChars(8)}`;
}

export function createIdempotencyKey(scope: string): string {
  return `${scope}-${randomChars(16)}`;
}

export function createOptionHash(entries: ReadonlyArray<{ groupId: string; optionId: string }>): string {
  if (entries.length === 0) {
    return "plain";
  }
  const sorted = [...entries].sort((a, b) => {
    if (a.groupId !== b.groupId) {
      return a.groupId < b.groupId ? -1 : 1;
    }
    return a.optionId < b.optionId ? -1 : a.optionId === b.optionId ? 0 : 1;
  });
  return sorted.map((entry) => `${entry.groupId}:${entry.optionId}`).join("|");
}
