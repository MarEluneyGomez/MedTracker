import { Ionicons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import type { Dose } from '../types/api';
import { formatClockTime } from '../utils/dates';

type Status = {
  icon: ComponentProps<typeof Ionicons>['name'];
  label: string;
  color: string;
};

function statusOf(dose: Dose): Status {
  if (dose.status === 'CONFIRMED') {
    const label = dose.confirmedAt ? `Tomada a las ${formatClockTime(dose.confirmedAt)}` : 'Tomada';
    return { icon: 'checkmark-circle', label, color: colors.success };
  }
  if (dose.status === 'SKIPPED') {
    return { icon: 'close-circle', label: 'No tomada', color: colors.textSecondary };
  }
  if (new Date(dose.scheduledAt) < new Date()) {
    return { icon: 'alert-circle', label: 'Atrasada', color: colors.warning };
  }
  return { icon: 'time-outline', label: 'Pendiente', color: colors.primary };
}

export default function DoseStatusLabel({ dose }: { dose: Dose }) {
  const status = statusOf(dose);
  return (
    <View style={styles.status}>
      <Ionicons name={status.icon} size={22} color={status.color} />
      <Text style={[styles.statusText, { color: status.color }]}>{status.label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  status: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  statusText: {
    fontSize: 16,
    fontWeight: '600',
  },
});
