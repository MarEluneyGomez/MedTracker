export type DosageUnit = 'mg' | 'ml' | 'tablet' | 'capsule' | 'drop' | 'iu' | 'application';

export type Dosage = {
  amount: number;
  unit: DosageUnit;
};

// Las abreviaturas (mg, ml, UI) no cambian en plural
export const DOSAGE_UNITS: Record<DosageUnit, { singular: string; plural: string }> = {
  mg: { singular: 'mg', plural: 'mg' },
  ml: { singular: 'ml', plural: 'ml' },
  tablet: { singular: 'comprimido', plural: 'comprimidos' },
  capsule: { singular: 'cápsula', plural: 'cápsulas' },
  drop: { singular: 'gota', plural: 'gotas' },
  iu: { singular: 'UI', plural: 'UI' },
  application: { singular: 'aplicación', plural: 'aplicaciones' },
};

// En español el decimal se escribe con coma
export function formatAmount(amount: number): string {
  return String(amount).replace('.', ',');
}

export function formatDosage({ amount, unit }: Dosage): string {
  const words = DOSAGE_UNITS[unit];
  return `${formatAmount(amount)} ${amount === 1 ? words.singular : words.plural}`;
}

// Acepta coma o punto decimal, porque el teclado del celular puede mostrar cualquiera
export function parseAmount(text: string): number | null {
  const amount = Number(text.trim().replace(',', '.'));
  return text.trim() !== '' && Number.isFinite(amount) && amount > 0 ? amount : null;
}

// null para textos con otro formato, como los cargados antes de estructurar la dosis
export function parseDosage(text: string): Dosage | null {
  const match = /^(\S+) (\S+)$/.exec(text.trim());
  if (!match) {
    return null;
  }
  const amount = parseAmount(match[1]);
  if (amount === null) {
    return null;
  }
  const word = match[2];
  for (const unit of Object.keys(DOSAGE_UNITS) as DosageUnit[]) {
    if (word === DOSAGE_UNITS[unit].singular || word === DOSAGE_UNITS[unit].plural) {
      return { amount, unit };
    }
  }
  return null;
}
