import AsyncStorage from '@react-native-async-storage/async-storage';
import type { Dose } from '../types/api';

const STORAGE_KEY = 'medtracker.pending-dose-actions';

export type DoseAction = 'confirm' | 'skip' | 'undo';

export type PendingDoseAction = {
  doseId: string;
  action: DoseAction;
  at: string;
};

export async function loadPendingDoseActions(): Promise<PendingDoseAction[]> {
  try {
    const stored = await AsyncStorage.getItem(STORAGE_KEY);
    return stored ? (JSON.parse(stored) as PendingDoseAction[]) : [];
  } catch {
    return [];
  }
}

async function savePendingDoseActions(actions: PendingDoseAction[]): Promise<void> {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(actions));
}

export async function queueDoseAction(pending: PendingDoseAction): Promise<void> {
  const actions = await loadPendingDoseActions();
  await savePendingDoseActions([
    ...actions.filter((action) => action.doseId !== pending.doseId),
    pending,
  ]);
}

export async function removePendingDoseAction(doseId: string): Promise<void> {
  const actions = await loadPendingDoseActions();
  await savePendingDoseActions(actions.filter((action) => action.doseId !== doseId));
}

export async function clearPendingDoseActions(): Promise<void> {
  await AsyncStorage.removeItem(STORAGE_KEY);
}

export function applyDoseAction(dose: Dose, { action, at }: PendingDoseAction): Dose {
  if (action === 'confirm') {
    return { ...dose, status: 'CONFIRMED', confirmedAt: at };
  }
  if (action === 'skip') {
    return { ...dose, status: 'SKIPPED', confirmedAt: null };
  }
  return { ...dose, status: 'PENDING', confirmedAt: null };
}

export function applyPendingDoseActions(doses: Dose[], pending: PendingDoseAction[]): Dose[] {
  const pendingByDose = new Map(pending.map((action) => [action.doseId, action]));
  return doses.map((dose) => {
    const action = pendingByDose.get(dose.id);
    return action ? applyDoseAction(dose, action) : dose;
  });
}
