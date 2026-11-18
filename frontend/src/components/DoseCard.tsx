import { Ionicons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import type { Dose } from '../types/api';
import { formatClockTime } from '../utils/dates';

type IconName = ComponentProps<typeof Ionicons>['name'];

type DoseCardProps = {
  dose: Dose;
  medicationName: string;
  dosage: string;
  busy: boolean;
  onConfirm: () => void;
  onSkip: () => void;
  onUndo: () => void;
};

type Status = {
  icon: IconName;
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

export default function DoseCard({
  dose,
  medicationName,
  dosage,
  busy,
  onConfirm,
  onSkip,
  onUndo,
}: DoseCardProps) {
  const status = statusOf(dose);
  const isPending = dose.status === 'PENDING';

  return (
    <View style={[styles.card, !isPending && styles.cardResolved]}>
      <View style={styles.header}>
        <Text style={styles.time}>{formatClockTime(dose.scheduledAt)}</Text>
        <View style={styles.info}>
          <Text style={styles.name}>{medicationName}</Text>
          <Text style={styles.dosage}>{dosage}</Text>
        </View>
      </View>

      <View style={styles.status}>
        <Ionicons name={status.icon} size={22} color={status.color} />
        <Text style={[styles.statusText, { color: status.color }]}>{status.label}</Text>
      </View>

      {isPending ? (
        <View style={styles.actions}>
          <ActionButton
            icon="checkmark"
            label="La tomé"
            filled
            disabled={busy}
            onPress={onConfirm}
          />
          <ActionButton icon="close" label="No la tomé" disabled={busy} onPress={onSkip} />
        </View>
      ) : dose.status === 'SKIPPED' ? (
        <ActionButton icon="checkmark" label="La tomé" filled disabled={busy} onPress={onConfirm} />
      ) : (
        <ActionButton icon="arrow-undo" label="Deshacer" disabled={busy} onPress={onUndo} />
      )}
    </View>
  );
}

type ActionButtonProps = {
  icon: IconName;
  label: string;
  filled?: boolean;
  disabled: boolean;
  onPress: () => void;
};

function ActionButton({ icon, label, filled = false, disabled, onPress }: ActionButtonProps) {
  const color = filled ? colors.onPrimary : colors.primary;
  return (
    <Pressable
      style={({ pressed }) => [
        styles.button,
        filled && styles.buttonFilled,
        (pressed || disabled) && styles.buttonDimmed,
      ]}
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
    >
      <Ionicons name={icon} size={22} color={color} />
      <Text style={[styles.buttonText, { color }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    gap: 12,
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  cardResolved: {
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  time: {
    fontSize: 48,
    lineHeight: 56,
    fontWeight: 'bold',
    fontVariant: ['tabular-nums'],
    color: colors.text,
  },
  info: {
    flex: 1,
  },
  name: {
    fontSize: 24,
    fontWeight: 'bold',
    color: colors.text,
  },
  dosage: {
    fontSize: 17,
    color: colors.textSecondary,
  },
  status: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  statusText: {
    fontSize: 16,
    fontWeight: '600',
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
  },
  button: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 52,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: colors.primary,
    borderRadius: 12,
  },
  buttonFilled: {
    backgroundColor: colors.primary,
  },
  buttonDimmed: {
    opacity: 0.6,
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
  },
});
