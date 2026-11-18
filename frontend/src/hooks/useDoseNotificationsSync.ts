import { useEffect } from 'react';
import { AppState } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { syncDoseNotifications } from '../services/doseNotifications';

// Al entrar y cada vez que la app vuelve a primer plano, para que siempre haya avisos por delante
export function useDoseNotificationsSync() {
  const { user } = useAuth();
  const userId = user?.id;

  useEffect(() => {
    if (!userId) {
      return;
    }
    syncDoseNotifications(userId);
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        syncDoseNotifications(userId);
      }
    });
    return () => subscription.remove();
  }, [userId]);
}
