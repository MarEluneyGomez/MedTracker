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

export function formatDayAndTime(date: Date): string {
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)} a las ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function isToday(date: Date): boolean {
  return toDayString(date) === toDayString(new Date());
}

export function isFutureDay(date: Date): boolean {
  return toDayString(date) > toDayString(new Date());
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

export const MONTH_NAMES = [
  'Enero',
  'Febrero',
  'Marzo',
  'Abril',
  'Mayo',
  'Junio',
  'Julio',
  'Agosto',
  'Septiembre',
  'Octubre',
  'Noviembre',
  'Diciembre',
];

export const DAY_NAMES = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

export function formatLongDate(date: Date): string {
  return `${DAY_NAMES[date.getDay()]} ${date.getDate()} de ${MONTH_NAMES[date.getMonth()].toLowerCase()}`;
}

// El backend manda fecha y hora juntas, como "2026-11-17T08:05:12"
export function formatClockTime(isoDateTime: string): string {
  const [, time] = isoDateTime.split('T');
  return time.slice(0, 5);
}
