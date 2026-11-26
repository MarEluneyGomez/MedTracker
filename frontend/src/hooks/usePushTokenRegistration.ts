import * as Notifications from 'expo-notifications';
import { useEffect } from 'react';
import { AppState } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { registerPushToken, sendPushToken } from '../services/pushToken';

export function usePushTokenRegistration() {
  const { user } = useAuth();
  const userId = user?.id;

  useEffect(() => {
    if (!userId) {
      return;
    }
    const register = () => {
      registerPushToken().catch(() => undefined);
    };
    register();
    const appState = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        register();
      }
    });
    const tokenChanges = Notifications.addPushTokenListener(({ data }) => {
      sendPushToken(data).catch(() => undefined);
    });
    return () => {
      appState.remove();
      tokenChanges.remove();
    };
  }, [userId]);
}
