import { upcomingDoses } from '../utils/upcomingDoses';
import { toLocalDateTime } from '../utils/dates';
import { getTodayDoses } from './doses';
import {
  cancelAllReminders,
  ensureNotificationPermission,
  replaceScheduledReminders,
  type ScheduledReminder,
} from './notifications';
import { applyPendingDoseActions, loadPendingDoseActions } from './pendingDoseActions';
import { getReminders } from './reminders';
import { getTreatmentsByUser } from './treatments';

const MAX_SCHEDULED_DOSES = 60;
const OPEN_APP_NOTICE_DELAY_MS = 60 * 1000;

// Como el push del backend: sin el medicamento, porque se ve con el teléfono bloqueado
const DOSE_TITLE = 'Recordatorio de medicación';
const DOSE_BODY = 'Es hora de tu toma';

let lastTask: Promise<void> = Promise.resolve();

// De a una por vez: dos sincronizaciones a la vez podrían mezclar lo que cancelan y programan.
// Si una falla (por ejemplo, sin conexión y sin datos guardados), quedan las que ya estaban.
function runInOrder(task: () => Promise<void>): Promise<void> {
  lastTask = lastTask.then(task).catch(() => undefined);
  return lastTask;
}

export function syncDoseNotifications(userId: string): Promise<void> {
  return runInOrder(() => scheduleUpcomingDoses(userId));
}

export function cancelDoseNotifications(): Promise<void> {
  return runInOrder(cancelAllReminders);
}

async function scheduleUpcomingDoses(userId: string): Promise<void> {
  if (!(await ensureNotificationPermission())) {
    return;
  }
  const [reminders, treatments, todayDoses, pending] = await Promise.all([
    getReminders(),
    getTreatmentsByUser(userId),
    getTodayDoses(userId),
    loadPendingDoseActions(),
  ]);
  const upcoming = upcomingDoses({
    reminders,
    treatments,
    doses: applyPendingDoseActions(todayDoses, pending),
    now: new Date(),
  });
  const scheduled: ScheduledReminder[] = upcoming.slice(0, MAX_SCHEDULED_DOSES).map((dose) => ({
    date: dose.at,
    title: DOSE_TITLE,
    body: DOSE_BODY,
    dose: { reminderId: dose.reminderId, scheduledAt: toLocalDateTime(dose.at) },
  }));
  if (upcoming.length > MAX_SCHEDULED_DOSES) {
    scheduled.push(openAppNotice(scheduled[scheduled.length - 1].date));
  }
  await replaceScheduledReminders(scheduled);
}

function openAppNotice(lastDoseDate: Date): ScheduledReminder {
  return {
    date: new Date(lastDoseDate.getTime() + OPEN_APP_NOTICE_DELAY_MS),
    title: 'Tus recordatorios',
    body: 'Abrí MedTracker para seguir recibiendo los avisos de tus tomas.',
  };
}
