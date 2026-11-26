import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { apiRequest } from './api';

export async function registerPushToken(): Promise<void> {
  if (Platform.OS !== 'android') {
    return;
  }
  const { data } = await Notifications.getDevicePushTokenAsync();
  await sendPushToken(data);
}

export function sendPushToken(token: string): Promise<void> {
  return apiRequest<void>('PUT', '/users/me/fcm-token', { fcmToken: token });
}
