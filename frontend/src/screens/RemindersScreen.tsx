import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type ScrollViewInstance,
} from 'react-native';
import AppButton from '../components/AppButton';
import SchedulePicker from '../components/SchedulePicker';
import ScheduleSaveBar from '../components/ScheduleSaveBar';
import Screen from '../components/Screen';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import type { TreatmentsStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { getMedication } from '../services/medications';
import { deleteReminder, getRemindersByTreatment, syncReminders } from '../services/reminders';
import { getTreatment } from '../services/treatments';
import { colors } from '../theme/colors';
import type { Medication, Reminder, Treatment } from '../types/api';
import { parseFrequency } from '../utils/frequency';
import {
  buildReminderMessage,
  formatDays,
  formatTime,
  initialSchedule,
  isPlanEmpty,
  matchesFrequency,
  parseTime,
  planSync,
  scheduleModeOf,
  scheduleTargets,
  sortByTime,
  validateSchedule,
  type Schedule,
} from '../utils/reminders';

const INTRO_BY_MODE = {
  fixed: 'Elegí a qué hora es la primera toma del día y armamos el resto.',
  free: 'Elegí a qué hora es cada toma.',
  manual: 'Esta frecuencia no se puede programar automáticamente. Agregá los horarios a mano.',
};

export default function RemindersScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<TreatmentsStackParamList>>();
  const route = useRoute<RouteProp<TreatmentsStackParamList, 'Reminders'>>();
  const { treatmentId } = route.params;
  const [treatment, setTreatment] = useState<Treatment | null>(null);
  const [medication, setMedication] = useState<Medication | null>(null);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [schedule, setSchedule] = useState<Schedule>(initialSchedule([]));
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useFlashScrollIndicators<ScrollViewInstance>(!loading);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [treatmentId]),
  );

  async function load() {
    try {
      const loadedTreatment = await getTreatment(treatmentId);
      const [loadedMedication, loadedReminders] = await Promise.all([
        getMedication(loadedTreatment.medicationId),
        getRemindersByTreatment(treatmentId),
      ]);
      setTreatment(loadedTreatment);
      setMedication(loadedMedication);
      setReminders(sortByTime(loadedReminders));
      setSchedule(initialSchedule(loadedReminders));
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : 'No se pudieron cargar los horarios.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSave() {
    if (!treatment || !medication) {
      return;
    }
    const frequency = parseFrequency(treatment.frequency);
    const targets = scheduleTargets(frequency, schedule);
    const scheduleError = validateSchedule(frequency, schedule);
    if (!targets || scheduleError) {
      setError(scheduleError);
      return;
    }

    setError(null);
    setSaving(true);
    const message = buildReminderMessage(treatment.dosage, medication.name);
    try {
      await syncReminders(treatmentId, planSync(reminders, targets, message), message);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudieron guardar los horarios.');
    } finally {
      // Se recarga también si falló, porque una parte de los cambios puede haberse guardado
      await load();
      setSaving(false);
    }
  }

  function confirmDelete(reminder: Reminder) {
    Alert.alert(
      'Eliminar horario',
      `¿Seguro que querés eliminar el horario de las ${formatTime(parseTime(reminder.time))}?`,
      [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Eliminar', style: 'destructive', onPress: () => handleDelete(reminder.id) },
      ],
    );
  }

  async function handleDelete(id: string) {
    try {
      await deleteReminder(id);
      await load();
    } catch (e) {
      Alert.alert(
        'No se pudo eliminar el horario',
        e instanceof ApiError ? e.message : 'Intentá de nuevo.',
      );
    }
  }

  if (loading) {
    return (
      <Screen hasHeader>
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      </Screen>
    );
  }

  if (loadError || !treatment || !medication) {
    return (
      <Screen hasHeader>
        <View style={styles.center}>
          <Text style={styles.message}>{loadError}</Text>
        </View>
      </Screen>
    );
  }

  const frequency = parseFrequency(treatment.frequency);
  const mode = scheduleModeOf(frequency);
  const message = buildReminderMessage(treatment.dosage, medication.name);
  const targets = scheduleTargets(frequency, schedule);
  const isSaved = targets !== null && isPlanEmpty(planSync(reminders, targets, message));
  const isOutOfSync =
    mode !== 'manual' &&
    frequency !== null &&
    reminders.length > 0 &&
    !matchesFrequency(reminders, frequency);

  return (
    <Screen hasHeader>
      <ScrollView
        ref={scrollRef}
        contentContainerStyle={styles.container}
        keyboardShouldPersistTaps="handled"
        persistentScrollbar
      >
        <Text style={styles.title}>Horarios</Text>
        <Text style={styles.subtitle}>
          {medication.name}: {treatment.dosage}, {treatment.frequency}
        </Text>

        {reminders.length === 0 ? <Text style={styles.message}>{INTRO_BY_MODE[mode]}</Text> : null}

        {isOutOfSync ? (
          <View style={styles.warning}>
            <Ionicons name="warning-outline" size={24} color={colors.warning} />
            <Text style={styles.warningText}>
              Los horarios guardados no coinciden con "{treatment.frequency}". Revisalos y guardá
              para reprogramarlos.
            </Text>
          </View>
        ) : null}

        {mode !== 'manual' ? (
          <>
            <SchedulePicker frequency={frequency} value={schedule} onChange={setSchedule} />
            <ScheduleSaveBar
              isSaved={isSaved}
              isNew={reminders.length === 0}
              saving={saving}
              error={error}
              onSave={handleSave}
            />
          </>
        ) : null}

        {mode === 'manual' ? (
          <>
            {reminders.map((reminder) => (
              <View key={reminder.id} style={styles.card}>
                <Pressable
                  style={styles.cardMain}
                  onPress={() =>
                    navigation.navigate('ReminderForm', { treatmentId, reminderId: reminder.id })
                  }
                  accessibilityRole="button"
                  accessibilityHint="Abre el horario para editarlo"
                >
                  <Ionicons name="alarm-outline" size={28} color={colors.primary} />
                  <View style={styles.cardInfo}>
                    <Text style={styles.time}>{formatTime(parseTime(reminder.time))}</Text>
                    <Text style={styles.days}>{formatDays(reminder.daysOfWeek)}</Text>
                  </View>
                  <View style={styles.action}>
                    <Ionicons name="create-outline" size={22} color={colors.primary} />
                    <Text style={styles.actionText}>Editar</Text>
                  </View>
                </Pressable>
                <Pressable
                  style={styles.deleteButton}
                  onPress={() => confirmDelete(reminder)}
                  accessibilityRole="button"
                >
                  <Ionicons name="trash-outline" size={20} color={colors.error} />
                  <Text style={styles.deleteText}>Eliminar</Text>
                </Pressable>
              </View>
            ))}
            <AppButton
              title="Agregar horario"
              onPress={() => navigation.navigate('ReminderForm', { treatmentId })}
            />
          </>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
    paddingBottom: 24,
  },
  center: {
    flex: 1,
    justifyContent: 'center',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  subtitle: {
    fontSize: 16,
    color: colors.textSecondary,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
  warning: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 16,
    borderRadius: 12,
    backgroundColor: colors.warningBackground,
  },
  warningText: {
    flex: 1,
    fontSize: 15,
    color: colors.warning,
  },
  card: {
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  cardMain: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    padding: 16,
  },
  cardInfo: {
    flex: 1,
  },
  time: {
    fontSize: 24,
    fontWeight: 'bold',
    fontVariant: ['tabular-nums'],
    color: colors.text,
  },
  days: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  action: {
    alignItems: 'center',
    gap: 2,
  },
  actionText: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.primary,
  },
  deleteButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  deleteText: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.error,
  },
});
