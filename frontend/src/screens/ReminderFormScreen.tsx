import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type ScrollViewInstance,
} from 'react-native';
import AppButton from '../components/AppButton';
import ChipSelector from '../components/ChipSelector';
import DaySelector from '../components/DaySelector';
import Screen from '../components/Screen';
import TimeField from '../components/TimeField';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import type { TreatmentsStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { getMedication } from '../services/medications';
import {
  createReminder,
  getReminder,
  updateReminder,
  type ReminderData,
} from '../services/reminders';
import { getTreatment } from '../services/treatments';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import type { Medication, Treatment, WeekDay } from '../types/api';
import {
  WEEK_DAYS,
  buildReminderMessage,
  parseTime,
  toBackendTime,
  type TimeOfDay,
} from '../utils/reminders';

const DEFAULT_TIME: TimeOfDay = { hours: 8, minutes: 0 };
const ALL_DAYS = 'Todos los días';
const SOME_DAYS = 'Algunos días';
const DAY_MODE_OPTIONS = [ALL_DAYS, SOME_DAYS];

export default function ReminderFormScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<TreatmentsStackParamList>>();
  const route = useRoute<RouteProp<TreatmentsStackParamList, 'ReminderForm'>>();
  const { treatmentId, reminderId } = route.params;
  const isEditing = reminderId !== undefined;
  const [treatment, setTreatment] = useState<Treatment | null>(null);
  const [medication, setMedication] = useState<Medication | null>(null);
  const [time, setTime] = useState<TimeOfDay>(DEFAULT_TIME);
  const [dayMode, setDayMode] = useState<string | null>(ALL_DAYS);
  const [days, setDays] = useState<WeekDay[]>([]);
  const [daysError, setDaysError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const scrollRef = useFlashScrollIndicators<ScrollViewInstance>(!loading);

  useEffect(() => {
    load();
  }, [treatmentId, reminderId]);

  async function load() {
    try {
      const loadedTreatment = await getTreatment(treatmentId);
      const [loadedMedication, reminder] = await Promise.all([
        getMedication(loadedTreatment.medicationId),
        reminderId ? getReminder(reminderId) : Promise.resolve(null),
      ]);
      setTreatment(loadedTreatment);
      setMedication(loadedMedication);
      if (reminder) {
        setTime(parseTime(reminder.time));
        setDayMode(reminder.daysOfWeek ? SOME_DAYS : ALL_DAYS);
        setDays(reminder.daysOfWeek ?? []);
      }
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : 'No se pudo cargar el horario.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSave() {
    if (!treatment || !medication) {
      return;
    }
    const isSomeDays = dayMode === SOME_DAYS;
    if (isSomeDays && days.length === 0) {
      setDaysError('Elegí al menos un día.');
      return;
    }

    setDaysError(null);
    setError(null);
    setSaving(true);
    const data: ReminderData = {
      time: toBackendTime(time),
      // Los 7 días se guardan como "todos los días", para que el backend los
      // reconozca como el mismo horario al controlar duplicados
      daysOfWeek: isSomeDays && days.length < WEEK_DAYS.length ? days : null,
      message: buildReminderMessage(treatment.dosage, medication.name),
    };
    try {
      if (reminderId) {
        await updateReminder(reminderId, data);
      } else {
        await createReminder(treatmentId, data);
      }
      navigation.goBack();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo guardar el horario.');
    } finally {
      setSaving(false);
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

  return (
    <Screen hasHeader>
      <ScrollView
        ref={scrollRef}
        contentContainerStyle={styles.container}
        keyboardShouldPersistTaps="handled"
        persistentScrollbar
      >
        <Text style={styles.title}>{isEditing ? 'Editar horario' : 'Nuevo horario'}</Text>
        <Text style={styles.subtitle}>
          {medication.name}: {treatment.dosage}
        </Text>
        <TimeField label="Hora de la toma" value={time} onChange={setTime} />
        <ChipSelector
          label="¿Qué días?"
          options={DAY_MODE_OPTIONS}
          value={dayMode}
          onChange={setDayMode}
        />
        {dayMode === SOME_DAYS ? (
          <DaySelector
            label="Días"
            value={days}
            onChange={setDays}
            error={daysError ?? undefined}
          />
        ) : null}
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <AppButton
          title={isEditing ? 'Guardar cambios' : 'Guardar horario'}
          onPress={handleSave}
          loading={saving}
        />
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
  error: fieldError,
});
