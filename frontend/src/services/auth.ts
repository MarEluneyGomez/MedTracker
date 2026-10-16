import type { User, UserRole } from '../types/api';
import { ApiError, apiRequest } from './api';
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

export type RegisterData = {
  name: string;
  email: string;
  password: string;
  role: UserRole;
};

export async function register(data: RegisterData): Promise<Session> {
  const user = await apiRequest<User>('POST', '/users/register', {
    name: data.name.trim(),
    email: data.email.trim().toLowerCase(),
    passwordHash: data.password,
    role: data.role,
  });
  return { user, token: null };
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
