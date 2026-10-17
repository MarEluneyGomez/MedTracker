export type FrequencyUnit = 'hours' | 'days' | 'weeks';

export type Frequency = {
  every: number;
  unit: FrequencyUnit;
};

const UNIT_WORDS: Record<FrequencyUnit, { singular: string; plural: string }> = {
  hours: { singular: 'hora', plural: 'horas' },
  days: { singular: 'día', plural: 'días' },
  weeks: { singular: 'semana', plural: 'semanas' },
};

export const COMMON_FREQUENCIES: Frequency[] = [
  { every: 1, unit: 'days' },
  { every: 12, unit: 'hours' },
  { every: 8, unit: 'hours' },
  { every: 6, unit: 'hours' },
];

export function formatFrequency({ every, unit }: Frequency): string {
  const words = UNIT_WORDS[unit];
  return `Cada ${every} ${every === 1 ? words.singular : words.plural}`;
}

export function parseFrequency(text: string): Frequency | null {
  const match = /^Cada (\d+) (\S+)$/.exec(text.trim());
  if (!match) {
    return null;
  }
  const every = Number(match[1]);
  const word = match[2];
  for (const unit of Object.keys(UNIT_WORDS) as FrequencyUnit[]) {
    if (word === UNIT_WORDS[unit].singular || word === UNIT_WORDS[unit].plural) {
      return { every, unit };
    }
  }
  return null;
}
