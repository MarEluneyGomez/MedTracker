import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

const REMINDER_CHANNEL_ID = 'dose-reminders';
const REMINDER_SOUND = 'recordatorio.wav';
export const DOSE_CATEGORY = 'dose';
export const CONFIRM_ACTION = 'confirm';

export type DoseReference = {
  reminderId: string;
  scheduledAt: string;
};

export type ScheduledReminder = {
  date: Date;
  title: string;
  body: string;
  dose?: DoseReference;
};

export function configureNotifications() {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
  Notifications.setNotificationCategoryAsync(DOSE_CATEGORY, [
    { identifier: CONFIRM_ACTION, buttonTitle: 'La tomé', options: { opensAppToForeground: true } },
  ]).catch(() => undefined);
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
    await scheduleReminder(reminder);
  }
}

function scheduleReminder(reminder: ScheduledReminder): Promise<string> {
  return Notifications.scheduleNotificationAsync({
    content: {
      title: reminder.title,
      body: reminder.body,
      sound: REMINDER_SOUND,
      data: reminder.dose,
      categoryIdentifier: reminder.dose ? DOSE_CATEGORY : undefined,
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.DATE,
      date: reminder.date,
      channelId: REMINDER_CHANNEL_ID,
      // Android no las demora para ahorrar batería: un aviso tarde puede hacer saltear una toma
      delivery: 'alarmClock',
    },
  });
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
      sound: REMINDER_SOUND,
    });
  } catch {
    // Queda el canal genérico
  }
}
