const listeners = new Set<() => void>();

export function subscribeToDoseChanges(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function notifyDoseChanged() {
  listeners.forEach((listener) => listener());
}
