import { Ionicons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import type { MedicalAppointment } from '../types/api';
import { formatClockTime, formatLongDate, fromBackendDate } from '../utils/dates';
import AppButton from './AppButton';

type Status = {
  icon: ComponentProps<typeof Ionicons>['name'];
  label: string;
  color: string;
};

function statusOf(appointment: MedicalAppointment, upcoming: boolean): Status {
  if (appointment.status === 'CANCELLED') {
    return { icon: 'close-circle', label: 'Cancelada', color: colors.textSecondary };
  }
  if (appointment.status === 'CONFIRMED') {
    return { icon: 'checkmark-circle', label: 'Confirmada', color: colors.success };
  }
  return upcoming
    ? { icon: 'hourglass-outline', label: 'Esperando tu confirmación', color: colors.warning }
    : { icon: 'help-circle-outline', label: 'No se confirmó', color: colors.textSecondary };
}

type AppointmentCardProps = {
  appointment: MedicalAppointment;
  doctorName: string;
  upcoming: boolean;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export default function AppointmentCard({
  appointment,
  doctorName,
  upcoming,
  busy,
  onConfirm,
  onCancel,
}: AppointmentCardProps) {
  const status = statusOf(appointment, upcoming);
  return (
    <View style={styles.card}>
      <Text style={styles.date}>
        {formatLongDate(fromBackendDate(appointment.dateTime))},{' '}
        {formatClockTime(appointment.dateTime)}
      </Text>
      <Text style={styles.doctor}>{doctorName}</Text>
      <Text style={styles.reason}>{appointment.reason}</Text>
      <View style={styles.status}>
        <Ionicons name={status.icon} size={22} color={status.color} />
        <Text style={[styles.statusText, { color: status.color }]}>{status.label}</Text>
      </View>
      {upcoming ? (
        <View style={styles.actions}>
          {appointment.status === 'PENDING' ? (
            <AppButton title="Confirmar" loading={busy} onPress={onConfirm} />
          ) : null}
          <AppButton title="Cancelar cita" variant="danger" loading={busy} onPress={onCancel} />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: 16,
    gap: 4,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  date: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  doctor: {
    fontSize: 16,
    color: colors.text,
  },
  reason: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  status: {
    marginTop: 4,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  statusText: {
    fontSize: 16,
    fontWeight: '600',
  },
  actions: {
    marginTop: 8,
    gap: 8,
  },
});
