import type { Dose } from '../types/api';
import { ApiError, apiRequest, isConnectionError } from './api';
import { toLocalDateTime } from '../utils/dates';
import { getOfflineDataSavedAt } from './connection';
import type { DoseReference } from './notifications';
import {
  applyDoseAction,
  applyPendingDoseActions,
  loadPendingDoseActions,
  queueDoseAction,
  removePendingDoseAction,
  type PendingDoseAction,
  type DoseAction,
} from './pendingDoseActions';

const BAD_REQUEST = 400;

export function getDoses(): Promise<Dose[]> {
  return apiRequest<Dose[]>('GET', '/doses');
}

export function getTodayDoses(userId: string): Promise<Dose[]> {
  return apiRequest<Dose[]>('GET', `/doses/user/${userId}`);
}

export function confirmDose(id: string, confirmedAt?: string): Promise<Dose> {
  return apiRequest<Dose>('PUT', `/doses/${id}/confirm`, confirmedAt ? { confirmedAt } : undefined);
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
      await sendPending(pending);
    } catch (e) {
      if (isConnectionError(e)) {
        return;
      }
    }
    await removePendingDoseAction(pending.doseId);
  }
}

function sendPending(pending: PendingDoseAction): Promise<Dose> {
  if (pending.action === 'confirm') {
    return confirmAtTimeTaken(pending.doseId, pending.at);
  }
  return SEND_ACTION[pending.action](pending.doseId);
}

// Si el reloj del celular está adelantado, el backend rechaza la hora: se confirma con la del servidor
async function confirmAtTimeTaken(id: string, at: string): Promise<Dose> {
  try {
    return await confirmDose(id, at);
  } catch (e) {
    if (e instanceof ApiError && e.status === BAD_REQUEST) {
      return confirmDose(id);
    }
    throw e;
  }
}

export async function confirmDoseFromNotification(
  userId: string,
  reference: DoseReference,
): Promise<void> {
  const day = reference.scheduledAt.slice(0, 10);
  const doses = applyPendingDoseActions(
    await getTodayDoses(userId),
    await loadPendingDoseActions(),
  );
  const dose = doses.find(
    (item) => item.reminderId === reference.reminderId && item.scheduledAt.slice(0, 10) === day,
  );
  if (dose && dose.status !== 'CONFIRMED') {
    await changeDose(dose, 'confirm');
  }
}
