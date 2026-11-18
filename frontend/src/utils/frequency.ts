export type FrequencyUnit = 'hours' | 'days' | 'weeks';

export type IntervalFrequency = {
  kind: 'interval';
  every: number;
  unit: FrequencyUnit;
};

export type TimesPerDayFrequency = {
  kind: 'timesPerDay';
  times: number;
};

export type Frequency = IntervalFrequency | TimesPerDayFrequency;

// "1 vez por día" sería lo mismo que "Cada 1 día"
export const MIN_TIMES_PER_DAY = 2;
export const MAX_TIMES_PER_DAY = 6;

const UNIT_WORDS: Record<FrequencyUnit, { singular: string; plural: string }> = {
  hours: { singular: 'hora', plural: 'horas' },
  days: { singular: 'día', plural: 'días' },
  weeks: { singular: 'semana', plural: 'semanas' },
};

export const COMMON_FREQUENCIES: Frequency[] = [
  { kind: 'interval', every: 1, unit: 'days' },
  { kind: 'interval', every: 12, unit: 'hours' },
  { kind: 'interval', every: 8, unit: 'hours' },
  { kind: 'interval', every: 6, unit: 'hours' },
];

export function formatFrequency(frequency: Frequency): string {
  if (frequency.kind === 'timesPerDay') {
    return `${frequency.times} veces por día`;
  }
  const words = UNIT_WORDS[frequency.unit];
  return `Cada ${frequency.every} ${frequency.every === 1 ? words.singular : words.plural}`;
}

// El texto guardado es canónico, así que dos frecuencias son iguales si se escriben igual
export function isSameFrequency(a: Frequency, b: Frequency): boolean {
  return formatFrequency(a) === formatFrequency(b);
}

export function parseFrequency(text: string): Frequency | null {
  const trimmed = text.trim();
  const timesPerDay = /^(\d+) veces por día$/.exec(trimmed);
  if (timesPerDay) {
    return { kind: 'timesPerDay', times: Number(timesPerDay[1]) };
  }
  const interval = /^Cada (\d+) (\S+)$/.exec(trimmed);
  if (!interval) {
    return null;
  }
  const every = Number(interval[1]);
  const word = interval[2];
  for (const unit of Object.keys(UNIT_WORDS) as FrequencyUnit[]) {
    if (word === UNIT_WORDS[unit].singular || word === UNIT_WORDS[unit].plural) {
      return { kind: 'interval', every, unit };
    }
  }
  return null;
}
