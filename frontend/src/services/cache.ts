import AsyncStorage from '@react-native-async-storage/async-storage';

const CACHE_PREFIX = 'medtracker.cache:';

export type CachedData<T> = {
  savedAt: string;
  data: T;
};

export async function saveToCache<T>(key: string, data: T): Promise<void> {
  const entry: CachedData<T> = { savedAt: new Date().toISOString(), data };
  await AsyncStorage.setItem(CACHE_PREFIX + key, JSON.stringify(entry));
}

export async function readFromCache<T>(key: string): Promise<CachedData<T> | null> {
  try {
    const stored = await AsyncStorage.getItem(CACHE_PREFIX + key);
    return stored ? (JSON.parse(stored) as CachedData<T>) : null;
  } catch {
    return null;
  }
}

// Son datos de salud: no deben quedar en el celular después de cerrar sesión
export async function clearCache(): Promise<void> {
  const keys = await AsyncStorage.getAllKeys();
  await AsyncStorage.multiRemove(keys.filter((key) => key.startsWith(CACHE_PREFIX)));
}
