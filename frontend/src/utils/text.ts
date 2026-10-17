// Solo la primera letra: el resto se respeta para no romper siglas como AAS o B12
export function capitalizeFirst(text: string): string {
  const clean = text.trim().replace(/\s+/g, ' ');
  return clean.charAt(0).toUpperCase() + clean.slice(1);
}

export function normalizeForSearch(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}
