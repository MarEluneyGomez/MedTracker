import type { User, UserRole } from '../types/api';
import { apiRequest } from './api';
import * as SecureStore from 'expo-secure-store';

export type Session = {
  user: User;
  token: string;
  refreshToken: string;
};

type SessionResponse = {
  accessToken: string;
  refreshToken: string;
  user: User;
};

function toSession({ accessToken, refreshToken, user }: SessionResponse): Session {
  return { user, token: accessToken, refreshToken };
}

export async function login(email: string, password: string): Promise<Session> {
  const response = await apiRequest<SessionResponse>(
    'POST',
    '/auth/login',
    { email: email.trim().toLowerCase(), password },
    { authenticated: false },
  );
  return toSession(response);
}

export async function refreshSession(refreshToken: string): Promise<Session> {
  const response = await apiRequest<SessionResponse>(
    'POST',
    '/auth/refresh',
    { refreshToken },
    { authenticated: false },
  );
  return toSession(response);
}

export function logout(refreshToken: string): Promise<void> {
  return apiRequest<void>('POST', '/auth/logout', { refreshToken }, { authenticated: false });
}

export type RegisterData = {
  name: string;
  email: string;
  password: string;
  role: UserRole;
};

// El registro no devuelve token: la sesión se obtiene con login() después
export function register(data: RegisterData): Promise<User> {
  return apiRequest<User>(
    'POST',
    '/users/register',
    {
      name: data.name.trim(),
      email: data.email.trim().toLowerCase(),
      passwordHash: data.password,
      role: data.role,
    },
    { authenticated: false },
  );
}

const SESSION_KEY = 'medtracker.session';

export async function saveSession(session: Session): Promise<void> {
  await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(session));
}

export async function loadSession(): Promise<Session | null> {
  const stored = await SecureStore.getItemAsync(SESSION_KEY);
  return stored ? (JSON.parse(stored) as Session) : null;
}

export async function clearSession(): Promise<void> {
  await SecureStore.deleteItemAsync(SESSION_KEY);
}
