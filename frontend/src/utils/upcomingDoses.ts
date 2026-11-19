import type { Dose, Reminder, Treatment, WeekDay } from '../types/api';
import { fromBackendDate, toDayString } from './dates';
import { parseTime } from './reminders';

// Se calcula un año: después solo se programan las más próximas
const DAYS_AHEAD = 365;
const MINUTES_PER_HOUR = 60;
const MS_PER_DAY = 24 * MINUTES_PER_HOUR * 60 * 1000;
// En el orden de Date.getDay()
const DAY_CODES: WeekDay[] = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

export type UpcomingDose = {
  reminderId: string;
  at: Date;
  message: string;
};

export type UpcomingDosesSource = {
  reminders: Reminder[];
  treatments: Treatment[];
  // Las tomas ya marcadas no se avisan
  doses: Dose[];
  now: Date;
};

// Mismas reglas con las que el backend genera las tomas de cada día
export function upcomingDoses({
  reminders,
  treatments,
  doses,
  now,
}: UpcomingDosesSource): UpcomingDose[] {
  const treatmentsById = new Map(treatments.map((treatment) => [treatment.id, treatment]));
  const marked = new Set(
    doses
      .filter((dose) => dose.status !== 'PENDING')
      .map((dose) => doseKey(dose.reminderId, dose.scheduledAt.slice(0, 10))),
  );
  const upcoming: UpcomingDose[] = [];
  for (let offset = 0; offset <= DAYS_AHEAD; offset++) {
    const day = dayFrom(now, offset);
    for (const reminder of reminders) {
      const treatment = treatmentsById.get(reminder.treatmentId);
      const at = atTime(day, reminder.time);
      if (
        treatment &&
        at > now &&
        isDueOn(reminder, treatment, day) &&
        !marked.has(doseKey(reminder.id, toDayString(day)))
      ) {
        upcoming.push({ reminderId: reminder.id, at, message: reminder.message });
      }
    }
  }
  return upcoming.sort((a, b) => a.at.getTime() - b.at.getTime());
}

function isDueOn(reminder: Reminder, treatment: Treatment, day: Date): boolean {
  return (
    reminder.active &&
    isTreatmentActiveOn(treatment, toDayString(day), reminder.time) &&
    appliesOn(reminder, day)
  );
}

function isTreatmentActiveOn(treatment: Treatment, day: string, time: string): boolean {
  const startDay = treatment.startDate.slice(0, 10);
  const endDay = treatment.endDate?.slice(0, 10) ?? null;
  if (treatment.completed || day < startDay || (endDay !== null && day > endDay)) {
    return false;
  }
  if (day === startDay && treatment.startTime && minutesOf(time) < minutesOf(treatment.startTime)) {
    return false;
  }
  return !(day === endDay && treatment.endTime && minutesOf(time) > minutesOf(treatment.endTime));
}

// Con intervalo cuenta cada N días desde su fecha de inicio; sin días elegidos, son todos
function appliesOn(reminder: Reminder, day: Date): boolean {
  if (reminder.intervalDays !== null) {
    if (!reminder.startDate) {
      return false;
    }
    const elapsed = Math.round(
      (day.getTime() - fromBackendDate(reminder.startDate).getTime()) / MS_PER_DAY,
    );
    return elapsed >= 0 && elapsed % reminder.intervalDays === 0;
  }
  const days = reminder.daysOfWeek;
  return !days || days.length === 0 || days.includes(DAY_CODES[day.getDay()]);
}

function dayFrom(date: Date, offset: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + offset);
}

function atTime(day: Date, time: string): Date {
  const { hours, minutes } = parseTime(time);
  return new Date(day.getFullYear(), day.getMonth(), day.getDate(), hours, minutes);
}

function minutesOf(time: string): number {
  const { hours, minutes } = parseTime(time);
  return hours * MINUTES_PER_HOUR + minutes;
}

function doseKey(reminderId: string, day: string): string {
  return `${reminderId}|${day}`;
}
