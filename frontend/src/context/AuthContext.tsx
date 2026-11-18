import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { Alert } from 'react-native';
import { ApiError, UNAUTHORIZED, setAuthToken, setSessionRenewer } from '../services/api';
import { clearCache } from '../services/cache';
import { markOnline } from '../services/connection';
import {
  clearSession,
  loadSession,
  login,
  logout,
  refreshSession,
  register,
  saveSession,
  type RegisterData,
  type Session,
} from '../services/auth';
import type { User } from '../types/api';

type AuthContextValue = {
  user: User | null;
  isRestoring: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (data: RegisterData) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isRestoring, setIsRestoring] = useState(true);

  useEffect(() => {
    async function restore() {
      const saved = await loadSession();
      // Las sesiones guardadas antes de la sesión renovable no tienen refresh token: se pide login
      if (saved?.refreshToken) {
        startSession(saved);
      } else {
        await clearSession();
      }
      setIsRestoring(false);
    }
    restore();
  }, []);

  useEffect(() => {
    setSessionRenewer(renewSession);
    return () => setSessionRenewer(null);
  }, []);

  function startSession(session: Session) {
    setAuthToken(session.token);
    setUser(session.user);
  }

  async function renewSession(): Promise<string | null> {
    const saved = await loadSession();
    if (!saved) {
      return null;
    }
    try {
      const session = await refreshSession(saved.refreshToken);
      await saveSession(session);
      startSession(session);
      return session.token;
    } catch (e) {
      // Sin conexión no se cierra la sesión: el pedido falla y se puede reintentar
      if (!(e instanceof ApiError) || e.status !== UNAUTHORIZED) {
        throw e;
      }
      await signOut();
      Alert.alert('Tu sesión expiró', 'Volvé a iniciar sesión para seguir usando la app.');
      return null;
    }
  }

  async function signIn(email: string, password: string) {
    const session = await login(email, password);
    await saveSession(session);
    startSession(session);
  }

  async function signUp(data: RegisterData) {
    await register(data);
    await signIn(data.email, data.password);
  }

  async function signOut() {
    setAuthToken(null);
    setUser(null);
    const saved = await loadSession();
    await clearSession();
    await clearCache();
    markOnline();

    // Si no se puede avisar al servidor, el refresh token vence solo
    if (saved) {
      logout(saved.refreshToken).catch(() => undefined);
    }
  }

  return (
    <AuthContext.Provider value={{ user, isRestoring, signIn, signUp, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth tiene que usarse dentro de AuthProvider');
  }
  return context;
}
