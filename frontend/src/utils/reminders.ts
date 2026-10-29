import type { Reminder, WeekDay } from '../types/api';
import type { Frequency, IntervalFrequency } from './frequency';

export type TimeOfDay = {
  hours: number;
  minutes: number;
};

// Un horario tal como debería quedar guardado
export type ReminderTarget = {
  time: TimeOfDay;
  daysOfWeek: WeekDay[] | null;
};

export type SyncPlan = {
  updates: { id: string; target: ReminderTarget }[];
  creates: ReminderTarget[];
  deletes: string[];
};

// Lo que el usuario elige en el selector de horarios. Se guardan los datos de
// los dos tipos de carga, así cambiar de frecuencia no pierde lo elegido.
export type Schedule = {
  firstTime: TimeOfDay;
  weekDay: WeekDay | null;
  freeTimes: TimeOfDay[];
};

// fixed: se calculan desde la primera toma; free: N horarios elegidos por el
// usuario; manual: se cargan uno por uno porque la frecuencia no se puede
// programar con recordatorios que se repiten cada semana
export type ScheduleMode = 'fixed' | 'free' | 'manual';

const DEFAULT_FIRST_TIME: TimeOfDay = { hours: 8, minutes: 0 };
const HOURS_PER_DAY = 24;
const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR;
const DAY_START_HOUR = 5;
const SPREAD_START_HOUR = 8;
const SPREAD_END_HOUR = 20;
const SPREAD_ROUNDING_MINUTES = 5;

export const WEEK_DAYS: { day: WeekDay; label: string }[] = [
  { day: 'MON', label: 'Lun' },
  { day: 'TUE', label: 'Mar' },
  { day: 'WED', label: 'Mié' },
  { day: 'THU', label: 'Jue' },
  { day: 'FRI', label: 'Vie' },
  { day: 'SAT', label: 'Sáb' },
  { day: 'SUN', label: 'Dom' },
];

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function formatTime({ hours, minutes }: TimeOfDay): string {
  return `${pad(hours)}:${pad(minutes)}`;
}

// El backend devuelve la hora como "00:00:00"
export function parseTime(text: string): TimeOfDay {
  const [hours, minutes] = text.split(':').map(Number);
  return { hours, minutes };
}

export function toBackendTime(time: TimeOfDay): string {
  return `${formatTime(time)}:00`;
}

export function formatDays(days: WeekDay[] | null): string {
  if (!days) {
    return 'Todos los días';
  }
  return WEEK_DAYS.filter(({ day }) => days.includes(day))
    .map(({ label }) => label)
    .join(', ');
}

export function buildReminderMessage(dosage: string, medicationName: string): string {
  return `Tomar ${dosage} de ${medicationName}`;
}

export function sortByTime(reminders: Reminder[]): Reminder[] {
  return [...reminders].sort((a, b) => a.time.localeCompare(b.time));
}

export function isWeekly(frequency: Frequency): boolean {
  return frequency.kind === 'interval' && frequency.unit === 'weeks';
}

export function hasRepeatedTimes(times: TimeOfDay[]): boolean {
  return new Set(times.map(formatTime)).size !== times.length;
}

function toMinutes({ hours, minutes }: TimeOfDay): number {
  return hours * MINUTES_PER_HOUR + minutes;
}

// Da la vuelta a la medianoche: 24:00 pasa a ser 00:00
function fromMinutes(total: number): TimeOfDay {
  const minutesOfDay = ((total % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
  return {
    hours: Math.floor(minutesOfDay / MINUTES_PER_HOUR),
    minutes: minutesOfDay % MINUTES_PER_HOUR,
  };
}

// Solo se pueden programar los intervalos que se repiten igual todas las
// semanas: cada N horas con N que divide 24, cada 1 día o cada 1 semana
function dosesPerDay({ every, unit }: IntervalFrequency): number | null {
  if (unit === 'hours') {
    return HOURS_PER_DAY % every === 0 ? HOURS_PER_DAY / every : null;
  }
  return every === 1 ? 1 : null;
}

export function scheduleModeOf(frequency: Frequency | null): ScheduleMode {
  if (frequency?.kind === 'timesPerDay') {
    return 'free';
  }
  if (frequency && dosesPerDay(frequency) !== null) {
    return 'fixed';
  }
  return 'manual';
}

export function fixedTimes(frequency: IntervalFrequency, first: TimeOfDay): TimeOfDay[] {
  const count = dosesPerDay(frequency) ?? 0;
  const stepMinutes = frequency.unit === 'hours' ? frequency.every * MINUTES_PER_HOUR : 0;
  return Array.from({ length: count }, (_, index) =>
    fromMinutes(toMinutes(first) + index * stepMinutes),
  );
}

// Propuesta para "N veces por día": repartidas entre las 8 y las 20, que es
// cuando la mayoría de las personas está despierta
export function spreadTimes(count: number): TimeOfDay[] {
  const start = SPREAD_START_HOUR * MINUTES_PER_HOUR;
  const span = (SPREAD_END_HOUR - SPREAD_START_HOUR) * MINUTES_PER_HOUR;
  return Array.from({ length: count }, (_, index) => {
    const offset = count === 1 ? 0 : (span * index) / (count - 1);
    const rounded = Math.round(offset / SPREAD_ROUNDING_MINUTES) * SPREAD_ROUNDING_MINUTES;
    return fromMinutes(start + rounded);
  });
}

// La primera toma del día es la primera desde las 5 de la mañana: con 00:00,
// 08:00 y 16:00 es la de las 08:00
export function firstOfDay(times: TimeOfDay[]): TimeOfDay {
  const dayStart = DAY_START_HOUR * MINUTES_PER_HOUR;
  const sinceDayStart = (time: TimeOfDay) =>
    (toMinutes(time) - dayStart + MINUTES_PER_DAY) % MINUTES_PER_DAY;
  return [...times].sort((a, b) => sinceDayStart(a) - sinceDayStart(b))[0];
}

export function initialSchedule(reminders: Reminder[]): Schedule {
  const times = sortByTime(reminders).map((reminder) => parseTime(reminder.time));
  return {
    firstTime: times.length > 0 ? firstOfDay(times) : DEFAULT_FIRST_TIME,
    weekDay: reminders[0]?.daysOfWeek?.[0] ?? null,
    freeTimes: times,
  };
}

// Si la cantidad de tomas no coincide (todavía no hay horarios o cambió la
// frecuencia), se usa la propuesta repartida en el día
export function freeTimesFor(schedule: Schedule, timesPerDay: number): TimeOfDay[] {
  return schedule.freeTimes.length === timesPerDay ? schedule.freeTimes : spreadTimes(timesPerDay);
}

// null cuando la frecuencia se programa a mano
export function scheduleTargets(
  frequency: Frequency | null,
  schedule: Schedule,
): ReminderTarget[] | null {
  if (!frequency || scheduleModeOf(frequency) === 'manual') {
    return null;
  }
  if (frequency.kind === 'timesPerDay') {
    return freeTimesFor(schedule, frequency.times).map((time) => ({ time, daysOfWeek: null }));
  }
  const daysOfWeek = isWeekly(frequency) && schedule.weekDay ? [schedule.weekDay] : null;
  return fixedTimes(frequency, schedule.firstTime).map((time) => ({ time, daysOfWeek }));
}

export function validateSchedule(frequency: Frequency | null, schedule: Schedule): string | null {
  if (frequency && isWeekly(frequency) && !schedule.weekDay) {
    return 'Elegí el día de la semana.';
  }
  if (
    frequency?.kind === 'timesPerDay' &&
    hasRepeatedTimes(freeTimesFor(schedule, frequency.times))
  ) {
    return 'Hay dos tomas a la misma hora. Cambiá una de las dos.';
  }
  return null;
}

function targetKey({ time, daysOfWeek }: ReminderTarget): string {
  return `${formatTime(time)} ${daysOfWeek?.join(',') ?? 'ALL'}`;
}

function toTarget(reminder: Reminder): ReminderTarget {
  return { time: parseTime(reminder.time), daysOfWeek: reminder.daysOfWeek ?? null };
}

function sameTargets(a: ReminderTarget[], b: ReminderTarget[]): boolean {
  const keys = new Set(a.map(targetKey));
  return a.length === b.length && b.every((target) => keys.has(targetKey(target)));
}

// Si los horarios guardados son los que corresponden a la frecuencia. En las
// fijas el horario es un ciclo, así que alcanza con recalcularlo desde
// cualquiera de sus tomas: se usa la primera del día.
export function matchesFrequency(reminders: Reminder[], frequency: Frequency): boolean {
  if (reminders.length === 0) {
    return false;
  }
  if (frequency.kind === 'timesPerDay') {
    return reminders.length === frequency.times && reminders.every((r) => !r.daysOfWeek);
  }
  const days = reminders[0].daysOfWeek ?? null;
  const hasValidDays = isWeekly(frequency) ? days?.length === 1 : days === null;
  if (!hasValidDays) {
    return false;
  }
  const first = firstOfDay(reminders.map((reminder) => parseTime(reminder.time)));
  const expected = fixedTimes(frequency, first).map((time) => ({ time, daysOfWeek: days }));
  return sameTargets(reminders.map(toTarget), expected);
}

// Los horarios que no cambian se conservan. El resto se reutiliza editándolo,
// así el backend mueve la toma pendiente de hoy en vez de perderla, y recién
// lo que sobra se elimina y lo que falta se crea. Un horario editado nunca
// coincide con otro guardado, así que el backend no lo rechaza como duplicado.
export function planSync(
  reminders: Reminder[],
  targets: ReminderTarget[],
  message: string,
): SyncPlan {
  const pending = [...targets];
  const unmatched: Reminder[] = [];
  const updates: SyncPlan['updates'] = [];

  for (const reminder of sortByTime(reminders)) {
    const key = targetKey(toTarget(reminder));
    const index = pending.findIndex((target) => targetKey(target) === key);
    if (index === -1) {
      unmatched.push(reminder);
      continue;
    }
    const [target] = pending.splice(index, 1);
    if (reminder.message !== message) {
      updates.push({ id: reminder.id, target });
    }
  }

  unmatched.slice(0, pending.length).forEach((reminder, index) => {
    updates.push({ id: reminder.id, target: pending[index] });
  });

  return {
    updates,
    creates: pending.slice(unmatched.length),
    deletes: unmatched.slice(pending.length).map((reminder) => reminder.id),
  };
}

export function isPlanEmpty({ updates, creates, deletes }: SyncPlan): boolean {
  return updates.length === 0 && creates.length === 0 && deletes.length === 0;
}
