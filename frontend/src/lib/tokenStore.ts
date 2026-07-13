import type { UserRole } from "@/types/domain";

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
  firstName: string;
  role: UserRole;
}

const KEY = "dlmp.session";

type Listener = (session: StoredSession | null) => void;
const listeners = new Set<Listener>();

function read(): StoredSession | null {
  const raw = localStorage.getItem(KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    return null;
  }
}

let cached: StoredSession | null = read();

export const tokenStore = {
  get(): StoredSession | null {
    return cached;
  },
  set(session: StoredSession) {
    cached = session;
    localStorage.setItem(KEY, JSON.stringify(session));
    listeners.forEach((l) => l(cached));
  },
  clear() {
    cached = null;
    localStorage.removeItem(KEY);
    listeners.forEach((l) => l(cached));
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
