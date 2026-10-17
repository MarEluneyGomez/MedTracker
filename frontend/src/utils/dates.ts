export function formatDate(isoDate: string): string {
  const [date] = isoDate.split('T');
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year}`;
}

export function fromBackendDate(isoDate: string): Date {
  const [date] = isoDate.split('T');
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function toDayString(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function toBackendDate(date: Date, time: string): string {
  return `${toDayString(date)}T${time}`;
}

export function toStartOfDay(date: Date): string {
  return toBackendDate(date, '00:00:00');
}

// El fin se guarda al final del día para que el último día del tratamiento también cuente
export function toEndOfDay(date: Date): string {
  return toBackendDate(date, '23:59:59');
}
