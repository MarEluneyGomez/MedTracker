import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type ScrollViewInstance,
} from 'react-native';
import AppButton from '../components/AppButton';
import ChipSelector from '../components/ChipSelector';
import DateField from '../components/DateField';
import DosagePicker from '../components/DosagePicker';
import FrequencyPicker from '../components/FrequencyPicker';
import MedicationPicker from '../components/MedicationPicker';
import SchedulePicker from '../components/SchedulePicker';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import type { TreatmentsStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import {
  createMedication,
  getMedication,
  getMedications,
  type NewMedication,
} from '../services/medications';
import { getRemindersByTreatment, syncReminders } from '../services/reminders';
import {
  createTreatment,
  getTreatment,
  updateTreatment,
  type TreatmentChanges,
} from '../services/treatments';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import type { Medication, Reminder } from '../types/api';
import { fromBackendDate, toEndOfDay, toStartOfDay } from '../utils/dates';
import { formatDosage, parseDosage, type Dosage } from '../utils/dosage';
import { formatFrequency, parseFrequency, type Frequency } from '../utils/frequency';
import {
  buildReminderMessage,
  initialSchedule,
  planSync,
  scheduleTargets,
  validateSchedule,
  type Schedule,
} from '../utils/reminders';

const HAS_END_OPTIONS = ['Sí', 'No'];

type FieldErrors = {
  medication?: string;
  dosage?: string;
  frequency?: string;
  schedule?: string;
  startDate?: string;
  hasEndDate?: string;
  endDate?: string;
};

export default function TreatmentFormScreen() {
  const { user } = useAuth();
  const navigation = useNavigation<NativeStackNavigationProp<TreatmentsStackParamList>>();
  const route = useRoute<RouteProp<TreatmentsStackParamList, 'TreatmentForm'>>();
  const treatmentId = route.params?.treatmentId;
  const isEditing = treatmentId !== undefined;
  const [medications, setMedications] = useState<Medication[]>([]);
  const [medication, setMedication] = useState<Medication | null>(null);
  const [dosage, setDosage] = useState<Dosage | null>(null);
  const [frequency, setFrequency] = useState<Frequency | null>(null);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [schedule, setSchedule] = useState<Schedule>(initialSchedule([]));
  const [startDate, setStartDate] = useState<Date | null>(new Date());
  const [hasEndDate, setHasEndDate] = useState<string | null>(null);
  const [endDate, setEndDate] = useState<Date | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(isEditing);
  const [loadError, setLoadError] = useState<string | null>(null);
  const scrollRef = useFlashScrollIndicators<ScrollViewInstance>(!loading);

  useEffect(() => {
    getMedications()
      .then(setMedications)
      .catch(() => setMedications([]));
  }, []);

  useEffect(() => {
    if (treatmentId) {
      loadTreatment(treatmentId);
    }
  }, [treatmentId]);

  async function loadTreatment(id: string) {
    try {
      const treatment = await getTreatment(id);
      const [treatmentMedication, savedReminders] = await Promise.all([
        getMedication(treatment.medicationId),
        getRemindersByTreatment(id),
      ]);
      setMedication(treatmentMedication);
      setReminders(savedReminders);
      setSchedule(initialSchedule(savedReminders));
      setDosage(parseDosage(treatment.dosage));
      setFrequency(parseFrequency(treatment.frequency));
      setStartDate(fromBackendDate(treatment.startDate));
      setHasEndDate(treatment.endDate ? 'Sí' : 'No');
      setEndDate(treatment.endDate ? fromBackendDate(treatment.endDate) : null);
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : 'No se pudo cargar el tratamiento.');
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateMedication(data: NewMedication) {
    const created = await createMedication(data);
    setMedications([...medications, created]);
    setMedication(created);
  }

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!medication) {
      errors.medication = 'Elegí un medicamento.';
    }
    if (!dosage) {
      errors.dosage = 'Indicá la cantidad y la unidad.';
    }
    if (!frequency) {
      errors.frequency = 'Indicá cada cuánto se toma.';
    }
    const scheduleError = validateSchedule(frequency, schedule);
    if (scheduleError) {
      errors.schedule = scheduleError;
    }
    if (!startDate) {
      errors.startDate = 'Elegí la fecha de inicio.';
    }
    if (!hasEndDate) {
      errors.hasEndDate = 'Indicá si el tratamiento tiene fecha de fin.';
    } else if (hasEndDate === 'Sí' && !endDate) {
      errors.endDate = 'Elegí la fecha de fin.';
    } else if (hasEndDate === 'Sí' && endDate && startDate && endDate < startDate) {
      errors.endDate = 'La fecha de fin no puede ser anterior a la de inicio.';
    }
    return errors;
  }

  async function handleSave() {
    const errors = validate();
    setFieldErrors(errors);
    if (
      Object.keys(errors).length > 0 ||
      !user ||
      !medication ||
      !dosage ||
      !frequency ||
      !startDate
    ) {
      return;
    }

    const changes: TreatmentChanges = {
      dosage: formatDosage(dosage),
      frequency: formatFrequency(frequency),
      startDate: toStartOfDay(startDate),
      endDate: hasEndDate === 'Sí' && endDate ? toEndOfDay(endDate) : null,
    };

    setError(null);
    setSaving(true);
    let savedId: string;
    try {
      savedId = treatmentId
        ? (await updateTreatment(treatmentId, changes)).id
        : (await createTreatment(user.id, { ...changes, medicationId: medication.id })).id;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo guardar el tratamiento.');
      setSaving(false);
      return;
    }

    try {
      await saveSchedule(savedId, changes.dosage, medication.name);
      // Un tratamiento nuevo que se programa a mano sigue en Horarios, para que
      // no quede sin recordatorios
      if (!isEditing && scheduleTargets(frequency, schedule) === null) {
        navigation.replace('Reminders', { treatmentId: savedId });
      } else {
        navigation.goBack();
      }
    } catch {
      // El tratamiento ya quedó guardado: se lleva a Horarios para reintentar
      // ahí, en vez de dejar el formulario como si no se hubiera guardado nada
      setSaving(false);
      Alert.alert(
        'Faltan los horarios',
        'El tratamiento se guardó, pero no se pudieron guardar los horarios. Revisalos en la pantalla de Horarios.',
        [
          {
            text: 'Ir a Horarios',
            onPress: () => navigation.replace('Reminders', { treatmentId: savedId }),
          },
        ],
      );
    }
  }

  async function saveSchedule(savedId: string, savedDosage: string, medicationName: string) {
    const targets = scheduleTargets(frequency, schedule);
    if (!targets) {
      return;
    }
    const message = buildReminderMessage(savedDosage, medicationName);
    await syncReminders(savedId, planSync(reminders, targets, message), message);
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

  if (loadError) {
    return (
      <Screen hasHeader>
        <View style={styles.center}>
          <Text style={styles.message}>{loadError}</Text>
          <AppButton title="Volver" onPress={() => navigation.goBack()} />
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
        <Text style={styles.title}>{isEditing ? 'Editar tratamiento' : 'Nuevo tratamiento'}</Text>
        <MedicationPicker
          medications={medications}
          selected={medication}
          onSelect={setMedication}
          onCreate={handleCreateMedication}
          error={fieldErrors.medication}
          locked={isEditing}
        />
        <DosagePicker value={dosage} onChange={setDosage} error={fieldErrors.dosage} />
        <FrequencyPicker value={frequency} onChange={setFrequency} error={fieldErrors.frequency} />
        <SchedulePicker
          frequency={frequency}
          value={schedule}
          onChange={setSchedule}
          error={fieldErrors.schedule}
        />
        <DateField
          label="Fecha de inicio"
          value={startDate}
          onChange={setStartDate}
          error={fieldErrors.startDate}
        />
        <ChipSelector
          label="¿Tiene fecha de fin?"
          options={HAS_END_OPTIONS}
          value={hasEndDate}
          onChange={setHasEndDate}
        />
        {fieldErrors.hasEndDate ? <Text style={styles.error}>{fieldErrors.hasEndDate}</Text> : null}
        {hasEndDate === 'Sí' ? (
          <DateField
            label="Fecha de fin"
            value={endDate}
            onChange={setEndDate}
            minimumDate={startDate ?? undefined}
            error={fieldErrors.endDate}
          />
        ) : null}
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <AppButton
          title={isEditing ? 'Guardar cambios' : 'Guardar tratamiento'}
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
    gap: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
  error: fieldError,
});
