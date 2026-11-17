import type { Reminder, WeekDay } from '../types/api';
import { formatDate, isToday } from './dates';
import type { Frequency, IntervalFrequency } from './frequency';

export type TimeOfDay = {
  hours: number;
  minutes: number;
};

// Todo en null = todos los días
export type Repetition = {
  daysOfWeek: WeekDay[] | null;
  intervalDays: number | null;
  startDate: string | null;
};

export type ReminderTarget = Repetition & {
  time: TimeOfDay;
};

export type SyncPlan = {
  updates: { id: string; target: ReminderTarget }[];
  creates: ReminderTarget[];
  deletes: string[];
};

// Guarda lo elegido para los dos tipos de carga, así cambiar de frecuencia no lo pierde
export type Schedule = {
  firstTime: TimeOfDay;
  weekDay: WeekDay | null;
  freeTimes: TimeOfDay[];
};

export type ScheduleMode = 'fixed' | 'free' | 'manual';

type FixedPattern = {
  count: number;
  stepHours: number;
  intervalDays: number | null;
};

const EVERY_DAY: Repetition = { daysOfWeek: null, intervalDays: null, startDate: null };
const DEFAULT_FIRST_TIME: TimeOfDay = { hours: 8, minutes: 0 };
const HOURS_PER_DAY = 24;
const DAYS_PER_WEEK = 7;
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

export function formatRepetition({ daysOfWeek, intervalDays, startDate }: Repetition): string {
  if (!intervalDays) {
    return formatDays(daysOfWeek);
  }
  return startDate
    ? `Cada ${intervalDays} días desde el ${formatDate(startDate)}`
    : `Cada ${intervalDays} días`;
}

export function buildReminderMessage(dosage: string, medicationName: string): string {
  return `Tomar ${dosage} de ${medicationName}`;
}

export function sortByTime(reminders: Reminder[]): Reminder[] {
  return [...reminders].sort((a, b) => a.time.localeCompare(b.time));
}

// Una vez por semana se programa por día de la semana, que es más fácil de
// entender que "cada 7 días desde el ..."
export function isWeekly(frequency: Frequency): boolean {
  return frequency.kind === 'interval' && frequency.unit === 'weeks' && frequency.every === 1;
}

export function hasRepeatedTimes(times: TimeOfDay[]): boolean {
  return new Set(times.map(formatTime)).size !== times.length;
}

function toMinutes({ hours, minutes }: TimeOfDay): number {
  return hours * MINUTES_PER_HOUR + minutes;
}

function fromMinutes(total: number): TimeOfDay {
  const minutesOfDay = ((total % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
  return {
    hours: Math.floor(minutesOfDay / MINUTES_PER_HOUR),
    minutes: minutesOfDay % MINUTES_PER_HOUR,
  };
}

// El backend repite un horario todos los días, en días de la semana o cada N
// días. Alcanza para cada N horas si N divide 24 o es múltiplo de 24, y para
// cualquier cantidad de días o semanas; el resto se programa a mano.
function fixedPattern(frequency: IntervalFrequency): FixedPattern | null {
  const { every, unit } = frequency;
  if (unit === 'hours' && HOURS_PER_DAY % every === 0) {
    return { count: HOURS_PER_DAY / every, stepHours: every, intervalDays: null };
  }
  const days = { hours: every / HOURS_PER_DAY, days: every, weeks: every * DAYS_PER_WEEK }[unit];
  if (!Number.isInteger(days)) {
    return null;
  }
  const usesInterval = days > 1 && !isWeekly(frequency);
  return { count: 1, stepHours: 0, intervalDays: usesInterval ? days : null };
}

export function scheduleModeOf(frequency: Frequency | null): ScheduleMode {
  if (frequency?.kind === 'timesPerDay') {
    return 'free';
  }
  if (frequency && fixedPattern(frequency) !== null) {
    return 'fixed';
  }
  return 'manual';
}

export function fixedTimes(frequency: IntervalFrequency, first: TimeOfDay): TimeOfDay[] {
  const pattern = fixedPattern(frequency);
  if (!pattern) {
    return [];
  }
  const stepMinutes = pattern.stepHours * MINUTES_PER_HOUR;
  return Array.from({ length: pattern.count }, (_, index) =>
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

export function freeTimesFor(schedule: Schedule, timesPerDay: number): TimeOfDay[] {
  return schedule.freeTimes.length === timesPerDay ? schedule.freeTimes : spreadTimes(timesPerDay);
}

// Los intervalos se cuentan desde la fecha de inicio del tratamiento
export function scheduleTargets(
  frequency: Frequency | null,
  schedule: Schedule,
  startDay: string | null,
): ReminderTarget[] | null {
  if (!frequency || scheduleModeOf(frequency) === 'manual') {
    return null;
  }
  if (frequency.kind === 'timesPerDay') {
    return freeTimesFor(schedule, frequency.times).map((time) => ({ time, ...EVERY_DAY }));
  }
  const intervalDays = fixedPattern(frequency)?.intervalDays ?? null;
  const repetition: Repetition = {
    daysOfWeek: isWeekly(frequency) && schedule.weekDay ? [schedule.weekDay] : null,
    intervalDays,
    startDate: intervalDays ? startDay : null,
  };
  return fixedTimes(frequency, schedule.firstTime).map((time) => ({ time, ...repetition }));
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

function targetKey({ time, daysOfWeek, intervalDays, startDate }: ReminderTarget): string {
  return [formatTime(time), daysOfWeek?.join(',') ?? 'ALL', intervalDays, startDate].join(' ');
}

function toTarget(reminder: Reminder): ReminderTarget {
  return {
    time: parseTime(reminder.time),
    daysOfWeek: reminder.daysOfWeek ?? null,
    intervalDays: reminder.intervalDays ?? null,
    startDate: reminder.startDate ?? null,
  };
}

function sameTargets(a: ReminderTarget[], b: ReminderTarget[]): boolean {
  const keys = new Set(a.map(targetKey));
  return a.length === b.length && b.every((target) => keys.has(targetKey(target)));
}

// En las fijas alcanza con recalcular desde la primera toma del día, porque el horario es un ciclo
export function matchesFrequency(
  reminders: Reminder[],
  frequency: Frequency,
  startDay: string,
): boolean {
  const schedule = initialSchedule(reminders);
  const expected = scheduleTargets(frequency, schedule, startDay);
  return (
    reminders.length > 0 &&
    expected !== null &&
    validateSchedule(frequency, schedule) === null &&
    sameTargets(reminders.map(toTarget), expected)
  );
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

function currentTimeOfDay(): TimeOfDay {
  const now = new Date();
  return { hours: now.getHours(), minutes: now.getMinutes() };
}

function firstDoseTime(frequency: Frequency | null, schedule: Schedule): TimeOfDay | null {
  if (frequency?.kind === 'timesPerDay') {
    return firstOfDay(freeTimesFor(schedule, frequency.times));
  }
  return scheduleModeOf(frequency) === 'fixed' ? schedule.firstTime : null;
}

// El día de inicio no se generan tomas anteriores a la primera, ni anteriores al
// momento de la carga si empieza hoy: no corresponde tomarlas y afectarían la adherencia
export function firstDayStartTime(
  frequency: Frequency | null,
  schedule: Schedule,
  startDate: Date,
): string | null {
  const first = firstDoseTime(frequency, schedule);
  if (!isToday(startDate)) {
    return first ? toBackendTime(first) : null;
  }
  const now = currentTimeOfDay();
  const latest = first && toMinutes(first) > toMinutes(now) ? first : now;
  return toBackendTime(latest);
}
