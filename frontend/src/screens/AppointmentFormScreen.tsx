import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import ChipSelector from '../components/ChipSelector';
import DateField from '../components/DateField';
import DoctorPicker from '../components/DoctorPicker';
import Screen from '../components/Screen';
import TextField from '../components/TextField';
import { useAuth } from '../context/AuthContext';
import type { AppointmentsStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { createAppointment, getDoctors } from '../services/appointments';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import type { Doctor } from '../types/api';
import { isToday, toDayString } from '../utils/dates';

const FIRST_SLOT_MINUTES = 8 * 60;
const LAST_SLOT_MINUTES = 17 * 60 + 30;
const SLOT_MINUTES = 30;
const MAX_REASON_LENGTH = 255;

type FieldErrors = {
  doctor?: string;
  time?: string;
  reason?: string;
};

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function slotsFor(day: Date, now: Date): string[] {
  const nowMinutes = now.getHours() * 60 + now.getMinutes();
  const slots: string[] = [];
  for (let minutes = FIRST_SLOT_MINUTES; minutes <= LAST_SLOT_MINUTES; minutes += SLOT_MINUTES) {
    if (!isToday(day) || minutes > nowMinutes) {
      slots.push(`${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`);
    }
  }
  return slots;
}

export default function AppointmentFormScreen() {
  const { user } = useAuth();
  const navigation = useNavigation<NativeStackNavigationProp<AppointmentsStackParamList>>();
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [loadingDoctors, setLoadingDoctors] = useState(true);
  const [doctor, setDoctor] = useState<Doctor | null>(null);
  const [date, setDate] = useState(new Date());
  const [time, setTime] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getDoctors()
      .then(setDoctors)
      .catch((e) =>
        setError(e instanceof ApiError ? e.message : 'No se pudo cargar la lista de médicos.'),
      )
      .finally(() => setLoadingDoctors(false));
  }, []);

  const slots = slotsFor(date, new Date());

  function handleDateChange(day: Date) {
    setDate(day);
    setTime(null);
  }

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!doctor) {
      errors.doctor = 'Elegí el médico.';
    }
    if (!time) {
      errors.time = 'Elegí la hora.';
    }
    if (!reason.trim()) {
      errors.reason = 'Escribí el motivo de la cita.';
    } else if (reason.trim().length > MAX_REASON_LENGTH) {
      errors.reason = `El motivo puede tener hasta ${MAX_REASON_LENGTH} caracteres.`;
    }
    return errors;
  }

  async function handleSave() {
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || !user || !doctor || !time) {
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await createAppointment({
        patientId: user.id,
        doctorId: doctor.id,
        dateTime: `${toDayString(date)}T${time}:00`,
        reason,
      });
      navigation.goBack();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo agendar la cita.');
      setSaving(false);
    }
  }

  if (loadingDoctors) {
    return (
      <Screen hasHeader>
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen hasHeader>
      <ScrollView
        contentContainerStyle={styles.container}
        keyboardShouldPersistTaps="handled"
        persistentScrollbar
      >
        <Text style={styles.title}>Nueva cita</Text>
        <DoctorPicker
          doctors={doctors}
          selected={doctor}
          onSelect={setDoctor}
          error={fieldErrors.doctor}
        />
        <DateField
          label="Fecha"
          value={date}
          onChange={handleDateChange}
          minimumDate={new Date()}
        />
        <View>
          {slots.length > 0 ? (
            <ChipSelector label="Hora" options={slots} value={time} onChange={setTime} />
          ) : (
            <Text style={styles.message}>Ya no quedan horarios para hoy. Elegí otra fecha.</Text>
          )}
          {fieldErrors.time ? <Text style={styles.error}>{fieldErrors.time}</Text> : null}
        </View>
        <TextField
          label="Motivo"
          value={reason}
          onChangeText={setReason}
          autoCapitalize="sentences"
          error={fieldErrors.reason}
        />
        <View style={styles.notice}>
          <Ionicons name="information-circle-outline" size={22} color={colors.textSecondary} />
          <Text style={styles.noticeText}>
            Al agendar la cita, el médico va a poder ver y cargar tu historial médico.
          </Text>
        </View>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <AppButton title="Agendar cita" onPress={handleSave} loading={saving} />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    justifyContent: 'center',
  },
  container: {
    gap: 16,
    paddingBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  message: {
    fontSize: 16,
    color: colors.textSecondary,
  },
  notice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
  },
  noticeText: {
    flex: 1,
    fontSize: 15,
    color: colors.textSecondary,
  },
  error: fieldError,
});
