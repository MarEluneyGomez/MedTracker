// Fecha de los datos guardados que se están mostrando; null si hay conexión
let offlineDataSavedAt: string | null = null;
let lastServerResponseAt: string | null = null;
const listeners = new Set<() => void>();

export function getOfflineDataSavedAt(): string | null {
  return offlineDataSavedAt;
}

export function subscribeToConnection(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function update(savedAt: string | null) {
  if (savedAt === offlineDataSavedAt) {
    return;
  }
  offlineDataSavedAt = savedAt;
  listeners.forEach((listener) => listener());
}

export function markOnline() {
  lastServerResponseAt = new Date().toISOString();
  update(null);
}

// Si se muestran varias copias, el aviso usa la más vieja
export function markShowingSavedData(savedAt: string) {
  const oldest =
    offlineDataSavedAt !== null && offlineDataSavedAt < savedAt ? offlineDataSavedAt : savedAt;
  update(oldest);
}

// Lo que está en pantalla llegó, como mucho, con la última respuesta del servidor
export function markConnectionLost() {
  markShowingSavedData(lastServerResponseAt ?? new Date().toISOString());
}
