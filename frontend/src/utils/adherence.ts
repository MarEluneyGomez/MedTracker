import type { Dose } from '../types/api';
import type { DoseDetails } from './doseDetails';

export type Period = 'week' | 'month' | 'all';

export type Adherence = {
  confirmed: number;
  total: number;
  percent: number | null;
};

export type AdherenceLevel = 'good' | 'fair' | 'low';

export type TreatmentAdherence = {
  treatmentId: string;
  medicationName: string;
  dosage: string;
  adherence: Adherence;
};

export type DayGroup = {
  day: string; // "2026-11-17"
  doses: Dose[];
};

export const PERIODS: { period: Period; label: string }[] = [
  { period: 'week', label: '7 días' },
  { period: 'month', label: '30 días' },
  { period: 'all', label: 'Todo' },
];

const PERIOD_DAYS: Record<Exclude<Period, 'all'>, number> = { week: 7, month: 30 };
const GOOD_ADHERENCE_PERCENT = 80;
const FAIR_ADHERENCE_PERCENT = 50;

// Solo cuentan las tomas cuya hora ya pasó.
export function dueDosesInPeriod(doses: Dose[], period: Period, now = new Date()): Dose[] {
  const due = doses.filter((dose) => new Date(dose.scheduledAt) <= now);
  if (period === 'all') {
    return due;
  }
  const firstDay = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() - (PERIOD_DAYS[period] - 1),
  );
  return due.filter((dose) => new Date(dose.scheduledAt) >= firstDay);
}

// RN-05: tomas confirmadas sobre tomas generadas en el período
export function adherenceOf(doses: Dose[]): Adherence {
  const confirmed = doses.filter((dose) => dose.status === 'CONFIRMED').length;
  const percent = doses.length > 0 ? Math.round((confirmed * 100) / doses.length) : null;
  return { confirmed, total: doses.length, percent };
}

export function adherenceLevel(percent: number): AdherenceLevel {
  if (percent >= GOOD_ADHERENCE_PERCENT) {
    return 'good';
  }
  return percent >= FAIR_ADHERENCE_PERCENT ? 'fair' : 'low';
}

export function adherenceByTreatment(
  doses: Dose[],
  details: Record<string, DoseDetails>,
): TreatmentAdherence[] {
  const dosesByTreatment = new Map<string, Dose[]>();
  for (const dose of doses) {
    const treatmentId = details[dose.reminderId]?.treatmentId;
    if (treatmentId) {
      dosesByTreatment.set(treatmentId, [...(dosesByTreatment.get(treatmentId) ?? []), dose]);
    }
  }
  return [...dosesByTreatment.entries()].map(([treatmentId, treatmentDoses]) => {
    const { medicationName, dosage } = details[treatmentDoses[0].reminderId];
    return { treatmentId, medicationName, dosage, adherence: adherenceOf(treatmentDoses) };
  });
}

export function groupByDay(doses: Dose[]): DayGroup[] {
  const dosesByDay = new Map<string, Dose[]>();
  for (const dose of doses) {
    const [day] = dose.scheduledAt.split('T');
    dosesByDay.set(day, [...(dosesByDay.get(day) ?? []), dose]);
  }
  return [...dosesByDay.entries()]
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([day, dayDoses]) => ({
      day,
      doses: [...dayDoses].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)),
    }));
}
