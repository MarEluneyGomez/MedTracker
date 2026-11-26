import { useNavigation, type NavigationProp, type ParamListBase } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { syncDoseNotifications } from '../services/doseNotifications';
import { confirmDoseFromNotification } from '../services/doses';
import { notifyDoseChanged } from '../services/doseEvents';
import { CONFIRM_ACTION, type DoseReference } from '../services/notifications';

export function useOpenTodayOnNotificationTap() {
  const navigation = useNavigation<NavigationProp<ParamListBase>>();
  const response = Notifications.useLastNotificationResponse();
  const { user } = useAuth();
  const userId = user?.id;

  useEffect(() => {
    if (!response || !userId) {
      return;
    }
    Notifications.clearLastNotificationResponse();
    confirmIfAsked(response, userId)
      .catch(() => undefined)
      .finally(() => navigation.navigate('Main', { screen: 'Today' }));
  }, [response, userId, navigation]);
}

async function confirmIfAsked(response: Notifications.NotificationResponse, userId: string) {
  if (response.actionIdentifier !== CONFIRM_ACTION) {
    return;
  }
  await Notifications.dismissNotificationAsync(response.notification.request.identifier);
  await confirmDoseFromNotification(
    userId,
    response.notification.request.content.data as DoseReference,
  );
  notifyDoseChanged();
  syncDoseNotifications(userId);
}
