import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

const REMINDER_CHANNEL_ID = 'medication-reminders';

export type ScheduledReminder = {
  date: Date;
  title: string;
  body: string;
};

// Con la app abierta el aviso también se muestra: la persona puede estar usándola a la hora de la toma
export function configureNotifications() {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

// En Android el canal tiene que existir antes de pedir el permiso
export async function ensureNotificationPermission(): Promise<boolean> {
  await createReminderChannel();

  const current = await Notifications.getPermissionsAsync();
  if (current.granted) {
    return true;
  }
  if (!current.canAskAgain) {
    return false;
  }
  const requested = await Notifications.requestPermissionsAsync();
  return requested.granted;
}

export async function replaceScheduledReminders(reminders: ScheduledReminder[]): Promise<void> {
  await Notifications.cancelAllScheduledNotificationsAsync();
  for (const reminder of reminders) {
    await Notifications.scheduleNotificationAsync({
      content: { title: reminder.title, body: reminder.body, sound: 'default' },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.DATE,
        date: reminder.date,
        channelId: REMINDER_CHANNEL_ID,
      },
    });
  }
}

export async function cancelAllReminders(): Promise<void> {
  await Notifications.cancelAllScheduledNotificationsAsync();
}

// En Expo Go crear el canal falla. Sin el canal, Android usa uno genérico de la librería
// y las notificaciones llegan igual, así que el error no corta la sincronización.
async function createReminderChannel(): Promise<void> {
  if (Platform.OS !== 'android') {
    return;
  }
  try {
    await Notifications.setNotificationChannelAsync(REMINDER_CHANNEL_ID, {
      name: 'Recordatorios de medicación',
      importance: Notifications.AndroidImportance.HIGH,
    });
  } catch {
    // Queda el canal genérico
  }
}
