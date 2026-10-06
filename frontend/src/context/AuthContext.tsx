import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { clearSession, loadSession, login, saveSession } from '../services/auth';
import type { User } from '../types/api';

type AuthContextValue = {
  user: User | null;
  isRestoring: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isRestoring, setIsRestoring] = useState(true);

  useEffect(() => {
    async function restore() {
      const saved = await loadSession();
      setUser(saved ? saved.user : null);
      setIsRestoring(false);
    }
    restore();
  }, []);

  async function signIn(email: string, password: string) {
    const session = await login(email, password);
    await saveSession(session);
    setUser(session.user);
  }

  async function signOut() {
    await clearSession();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, isRestoring, signIn, signOut }}>
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
