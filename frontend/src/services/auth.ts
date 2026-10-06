import type { User } from '../types/api';
import { ApiError } from './api';
import { MOCK_PASSWORD, mockUsers } from './mocks/users';
import * as SecureStore from 'expo-secure-store';

export type Session = {
  user: User;
  token: string | null;
};

// MOCK: el backend todavía no tiene endpoint de login (CU-02)
export async function login(email: string, password: string): Promise<Session> {
  const user = mockUsers.find((u) => u.email === email.trim().toLowerCase());

  if (!user || password !== MOCK_PASSWORD) {
    throw new ApiError(401, 'Email o contraseña incorrectos.');
  }

  return { user, token: 'mock-token' };
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
