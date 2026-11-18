import type { Dose } from '../types/api';
import { apiRequest, isConnectionError } from './api';
import { toLocalDateTime } from '../utils/dates';
import { getOfflineDataSavedAt } from './connection';
import {
  applyDoseAction,
  loadPendingDoseActions,
  queueDoseAction,
  removePendingDoseAction,
  type DoseAction,
} from './pendingDoseActions';

export function getDoses(): Promise<Dose[]> {
  return apiRequest<Dose[]>('GET', '/doses');
}

export function getTodayDoses(userId: string): Promise<Dose[]> {
  return apiRequest<Dose[]>('GET', `/doses/user/${userId}`);
}

export function confirmDose(id: string): Promise<Dose> {
  return apiRequest<Dose>('PUT', `/doses/${id}/confirm`);
}

export function skipDose(id: string): Promise<Dose> {
  return apiRequest<Dose>('PUT', `/doses/${id}/skip`);
}

export function undoDose(id: string): Promise<Dose> {
  return apiRequest<Dose>('PUT', `/doses/${id}/undo`);
}

const SEND_ACTION: Record<DoseAction, (id: string) => Promise<Dose>> = {
  confirm: confirmDose,
  skip: skipDose,
  undo: undoDose,
};

export type DoseChange = {
  dose: Dose;
  isPendingSync: boolean;
};

// Sin conexión la acción queda en espera y se devuelve la toma como va a quedar
export async function changeDose(dose: Dose, action: DoseAction): Promise<DoseChange> {
  // Si ya se sabe que no hay conexión, se guarda directo en lugar de esperar a que falle
  if (getOfflineDataSavedAt() !== null) {
    return keepForLater(dose, action);
  }
  await syncPendingDoseActions();
  try {
    const updated = await SEND_ACTION[action](dose.id);
    return { dose: updated, isPendingSync: false };
  } catch (e) {
    if (!isConnectionError(e)) {
      throw e;
    }
    return keepForLater(dose, action);
  }
}

async function keepForLater(dose: Dose, action: DoseAction): Promise<DoseChange> {
  const pending = { doseId: dose.id, action, at: toLocalDateTime(new Date()) };
  await queueDoseAction(pending);
  return { dose: applyDoseAction(dose, pending), isPendingSync: true };
}

// Se mandan en orden. Si sigue sin conexión, quedan para la próxima. Si el
// servidor rechaza una (por ejemplo, otra persona ya marcó esa toma), se
// descarta: al recargar la pantalla se ve el estado real.
export async function syncPendingDoseActions(): Promise<void> {
  for (const pending of await loadPendingDoseActions()) {
    try {
      await SEND_ACTION[pending.action](pending.doseId);
    } catch (e) {
      if (isConnectionError(e)) {
        return;
      }
    }
    await removePendingDoseAction(pending.doseId);
  }
}
