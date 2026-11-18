import { useNavigation, type NavigationProp, type ParamListBase } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { useEffect } from 'react';

// Todas las notificaciones son de tomas: al tocar una se abre Hoy para marcarla
export function useOpenTodayOnNotificationTap() {
  const navigation = useNavigation<NavigationProp<ParamListBase>>();
  const response = Notifications.useLastNotificationResponse();

  useEffect(() => {
    if (!response) {
      return;
    }
    navigation.navigate('Main', { screen: 'Today' });
    Notifications.clearLastNotificationResponse();
  }, [response, navigation]);
}
