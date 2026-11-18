import { useSyncExternalStore } from 'react';
import { getOfflineDataSavedAt, subscribeToConnection } from '../services/connection';

export function useOfflineDataSavedAt(): string | null {
  return useSyncExternalStore(subscribeToConnection, getOfflineDataSavedAt);
}
