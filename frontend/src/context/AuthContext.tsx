import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { Alert } from 'react-native';
import { setAuthToken, setSessionExpiredHandler } from '../services/api';
import {
  clearSession,
  loadSession,
  login,
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
      // Las sesiones guardadas antes del JWT no tienen token: se descartan y se pide login
      if (saved?.token) {
        startSession(saved);
      } else {
        await clearSession();
      }
      setIsRestoring(false);
    }
    restore();
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      signOut();
      Alert.alert('Tu sesión expiró', 'Volvé a iniciar sesión para seguir usando la app.');
    });
    return () => setSessionExpiredHandler(null);
  }, []);

  function startSession(session: Session) {
    setAuthToken(session.token);
    setUser(session.user);
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
    await clearSession();
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
