import { createContext, useContext, type ReactNode } from 'react';
import { useAuth } from './AuthContext';

type SelectedPatient = {
  id: string;
  name: string;
};

type ActivePatient = {
  id: string | undefined;
  name: string | undefined;
  isOwn: boolean;
};

const ActivePatientContext = createContext<SelectedPatient | null>(null);

export function ActivePatientProvider({
  patient,
  children,
}: {
  patient: SelectedPatient;
  children: ReactNode;
}) {
  return <ActivePatientContext.Provider value={patient}>{children}</ActivePatientContext.Provider>;
}

export function useActivePatient(): ActivePatient {
  const { user } = useAuth();
  const selected = useContext(ActivePatientContext);
  if (selected) {
    return { id: selected.id, name: selected.name, isOwn: false };
  }
  return { id: user?.id, name: user?.name, isOwn: true };
}
