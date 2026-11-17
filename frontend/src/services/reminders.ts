import type { Reminder } from '../types/api';
import {
  toBackendTime,
  type ReminderTarget,
  type Repetition,
  type SyncPlan,
} from '../utils/reminders';
import { apiRequest } from './api';

export type ReminderData = Repetition & {
  time: string; // formato "08:30:00"
  message: string;
};

export function getRemindersByTreatment(treatmentId: string): Promise<Reminder[]> {
  return apiRequest<Reminder[]>('GET', `/reminders/treatment/${treatmentId}`);
}

export function getReminder(id: string): Promise<Reminder> {
  return apiRequest<Reminder>('GET', `/reminders/${id}`);
}

export function createReminder(treatmentId: string, data: ReminderData): Promise<Reminder> {
  return apiRequest<Reminder>('POST', '/reminders/register', {
    treatment: { id: treatmentId },
    time: data.time,
    daysOfWeek: data.daysOfWeek,
    intervalDays: data.intervalDays,
    startDate: data.startDate,
    message: data.message.trim(),
  });
}

export function updateReminder(id: string, data: ReminderData): Promise<Reminder> {
  return apiRequest<Reminder>('PUT', `/reminders/${id}`, {
    time: data.time,
    daysOfWeek: data.daysOfWeek,
    intervalDays: data.intervalDays,
    startDate: data.startDate,
    message: data.message.trim(),
  });
}

export function deleteReminder(id: string): Promise<void> {
  return apiRequest<void>('DELETE', `/reminders/${id}`);
}

function toReminderData({ time, ...repetition }: ReminderTarget, message: string): ReminderData {
  return { time: toBackendTime(time), ...repetition, message };
}

export async function syncReminders(
  treatmentId: string,
  plan: SyncPlan,
  message: string,
): Promise<void> {
  await Promise.all([
    ...plan.updates.map(({ id, target }) => updateReminder(id, toReminderData(target, message))),
    ...plan.creates.map((target) => createReminder(treatmentId, toReminderData(target, message))),
    ...plan.deletes.map((id) => deleteReminder(id)),
  ]);
}
