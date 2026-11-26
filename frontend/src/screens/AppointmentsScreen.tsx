import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, SectionList, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import AppointmentCard from '../components/AppointmentCard';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import { ApiError } from '../services/api';
import {
  cancelAppointment,
  confirmAppointment,
  getAppointments,
  getDoctors,
  getConfirmedPatients,
} from '../services/appointments';
import { colors } from '../theme/colors';
import type { MedicalAppointment } from '../types/api';
import { groupAppointments, isUpcoming, type AppointmentSection } from '../utils/appointments';
import { formatClockTime, formatLongDate, fromBackendDate } from '../utils/dates';

export default function AppointmentsScreen() {
  const { user } = useAuth();
  const isDoctor = user?.role === 'DOCTOR';
  const [appointments, setAppointments] = useState<MedicalAppointment[]>([]);
  const [names, setNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const listRef =
    useFlashScrollIndicators<SectionList<MedicalAppointment, AppointmentSection>>(!loading);

  async function load() {
    try {
      const userAppointments = await getAppointments();
      setNames(isDoctor ? await loadPatientNames(userAppointments) : await loadDoctorNames());
      setAppointments(userAppointments);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudieron cargar las citas.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useFocusEffect(
    useCallback(() => {
      load();
    }, []),
  );

  function handleRefresh() {
    setRefreshing(true);
    load();
  }

  function handleRetry() {
    setLoading(true);
    load();
  }

  async function changeStatus(id: string, action: (id: string) => Promise<MedicalAppointment>) {
    setBusyId(id);
    try {
      const updated = await action(id);
      setAppointments((current) => current.map((item) => (item.id === id ? updated : item)));
    } catch (e) {
      Alert.alert('No se pudo guardar', e instanceof ApiError ? e.message : 'Intentá de nuevo.');
      await load();
    } finally {
      setBusyId(null);
    }
  }

  function confirmCancel(appointment: MedicalAppointment) {
    const day = formatLongDate(fromBackendDate(appointment.dateTime)).toLowerCase();
    const time = formatClockTime(appointment.dateTime);
    Alert.alert(
      'Cancelar cita',
      `¿Cancelar la cita del ${day} a las ${time}? Después no se puede reabrir.`,
      [
        { text: 'Volver', style: 'cancel' },
        {
          text: 'Cancelar cita',
          style: 'destructive',
          onPress: () => changeStatus(appointment.id, cancelAppointment),
        },
      ],
    );
  }

  function personName(appointment: MedicalAppointment): string {
    if (isDoctor) {
      return names[appointment.patientId] ?? 'Paciente sin confirmar';
    }
    return names[appointment.doctorId] ?? 'Médico';
  }

  if (loading) {
    return (
      <Screen>
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <View style={styles.center}>
          <Text style={styles.message}>{error}</Text>
          <AppButton title="Reintentar" onPress={handleRetry} />
        </View>
      </Screen>
    );
  }

  const now = new Date();

  return (
    <Screen>
      <SectionList
        ref={listRef}
        sections={groupAppointments(appointments, now)}
        persistentScrollbar
        stickySectionHeadersEnabled={false}
        keyExtractor={(item) => item.id}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        contentContainerStyle={styles.list}
        ListHeaderComponent={<Text style={styles.title}>Mis citas</Text>}
        ListEmptyComponent={<Text style={styles.message}>Todavía no tenés citas.</Text>}
        renderSectionHeader={({ section }) => (
          <Text style={styles.sectionTitle}>{section.title}</Text>
        )}
        renderItem={({ item }) => (
          <AppointmentCard
            appointment={item}
            personName={personName(item)}
            viewer={isDoctor ? 'doctor' : 'patient'}
            upcoming={isUpcoming(item, now)}
            busy={busyId === item.id}
            onConfirm={isDoctor ? undefined : () => changeStatus(item.id, confirmAppointment)}
            onCancel={() => confirmCancel(item)}
          />
        )}
      />
    </Screen>
  );
}

async function loadDoctorNames(): Promise<Record<string, string>> {
  const doctors = await getDoctors();
  return Object.fromEntries(doctors.map((doctor) => [doctor.id, doctor.name]));
}

async function loadPatientNames(
  appointments: MedicalAppointment[],
): Promise<Record<string, string>> {
  const patients = await getConfirmedPatients(appointments);
  return Object.fromEntries(patients.map((patient) => [patient.id, patient.name]));
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    justifyContent: 'center',
    gap: 16,
  },
  list: {
    gap: 12,
    paddingBottom: 24,
  },
  title: {
    marginTop: 16,
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  sectionTitle: {
    marginTop: 8,
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
});
