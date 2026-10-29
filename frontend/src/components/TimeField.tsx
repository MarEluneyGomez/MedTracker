import { Ionicons } from '@expo/vector-icons';
import { useState, type ComponentProps } from 'react';
import { Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldError, fieldLabel } from '../theme/typography';
import { formatTime, type TimeOfDay } from '../utils/reminders';
import AppButton from './AppButton';

const HOURS_PER_DAY = 24;
const MINUTES_PER_HOUR = 60;
const MINUTES_STEP = 5;

type TimeFieldProps = {
  label: string;
  value: TimeOfDay;
  onChange: (time: TimeOfDay) => void;
  error?: string;
};

function wrap(value: number, limit: number): number {
  return (value + limit) % limit;
}

function twoDigits(value: number): string {
  return String(value).padStart(2, '0');
}

export default function TimeField({ label, value, onChange, error }: TimeFieldProps) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(value);

  function openPicker() {
    setDraft(value);
    setOpen(true);
  }

  function changeHours(direction: number) {
    setDraft({ ...draft, hours: wrap(draft.hours + direction, HOURS_PER_DAY) });
  }

  function changeMinutes(direction: number) {
    setDraft({
      ...draft,
      minutes: wrap(draft.minutes + direction * MINUTES_STEP, MINUTES_PER_HOUR),
    });
  }

  function confirm() {
    onChange(draft);
    setOpen(false);
  }

  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <Pressable
        style={[styles.box, error ? styles.boxError : null]}
        onPress={openPicker}
        accessibilityRole="button"
        accessibilityHint="Abre el selector de hora"
      >
        <Ionicons name="time-outline" size={20} color={colors.textSecondary} />
        <Text style={styles.value}>{formatTime(value)}</Text>
      </Pressable>
      {error ? <Text style={styles.error}>{error}</Text> : null}

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <View style={styles.overlay}>
          <View style={styles.sheet}>
            <Text style={styles.sheetTitle}>{label}</Text>
            <Text style={styles.hint}>Tocá los números para escribirlos o usá + y −</Text>
            <View style={styles.dials}>
              <Dial
                value={draft.hours}
                limit={HOURS_PER_DAY}
                name="Hora"
                stepLabel="1 hora"
                onStep={changeHours}
                onType={(hours) => setDraft({ ...draft, hours })}
              />
              <Text style={styles.separator}>:</Text>
              <Dial
                value={draft.minutes}
                limit={MINUTES_PER_HOUR}
                name="Minutos"
                stepLabel={`${MINUTES_STEP} minutos`}
                onStep={changeMinutes}
                onType={(minutes) => setDraft({ ...draft, minutes })}
              />
            </View>
            <AppButton title="Listo" onPress={confirm} />
            <Pressable
              style={styles.cancel}
              onPress={() => setOpen(false)}
              accessibilityRole="button"
            >
              <Text style={styles.cancelText}>Cancelar</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </View>
  );
}

type DialProps = {
  value: number;
  limit: number;
  name: string;
  stepLabel: string;
  onStep: (direction: number) => void;
  onType: (value: number) => void;
};

function Dial({ value, limit, name, stepLabel, onStep, onType }: DialProps) {
  // null = no se está escribiendo; se muestra el valor con dos dígitos
  const [typedText, setTypedText] = useState<string | null>(null);
  const typedValue = Number(typedText);
  const isTypedValid = typedText !== '' && typedValue < limit;

  function handleChangeText(text: string) {
    const digits = text.replace(/\D/g, '');
    setTypedText(digits);
    // Se aplica en el momento y no al salir del campo, para que "Listo"
    // guarde lo escrito aunque se toque sin cerrar el teclado
    if (digits !== '' && Number(digits) < limit) {
      onType(Number(digits));
    }
  }

  function step(direction: number) {
    setTypedText(null);
    onStep(direction);
  }

  return (
    <View style={styles.dial}>
      <StepButton icon="add" accessibilityLabel={`Sumar ${stepLabel}`} onPress={() => step(1)} />
      <TextInput
        style={[styles.dialValue, typedText !== null && !isTypedValid && styles.dialValueInvalid]}
        value={typedText ?? twoDigits(value)}
        onChangeText={handleChangeText}
        onBlur={() => setTypedText(null)}
        keyboardType="number-pad"
        maxLength={2}
        selectTextOnFocus
        accessibilityLabel={name}
      />
      <StepButton
        icon="remove"
        accessibilityLabel={`Restar ${stepLabel}`}
        onPress={() => step(-1)}
      />
    </View>
  );
}

type StepButtonProps = {
  icon: ComponentProps<typeof Ionicons>['name'];
  accessibilityLabel: string;
  onPress: () => void;
};

function StepButton({ icon, accessibilityLabel, onPress }: StepButtonProps) {
  return (
    <Pressable
      style={({ pressed }) => [styles.stepButton, pressed && styles.pressed]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
    >
      <Ionicons name={icon} size={32} color={colors.onPrimary} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  label: fieldLabel,
  box: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: 12,
    backgroundColor: colors.surface,
  },
  boxError: {
    borderColor: colors.error,
  },
  value: {
    fontSize: 16,
    color: colors.text,
  },
  error: { ...fieldError, marginTop: 4 },
  overlay: {
    flex: 1,
    justifyContent: 'center',
    padding: 16,
    backgroundColor: colors.overlay,
  },
  sheet: {
    gap: 16,
    padding: 16,
    borderRadius: 16,
    backgroundColor: colors.surface,
  },
  sheetTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
  },
  hint: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  dials: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 16,
  },
  dial: {
    alignItems: 'center',
    gap: 8,
  },
  dialValue: {
    minWidth: 88,
    paddingVertical: 4,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: 12,
    textAlign: 'center',
    fontSize: 48,
    fontWeight: 'bold',
    fontVariant: ['tabular-nums'],
    color: colors.text,
  },
  dialValueInvalid: {
    borderColor: colors.error,
    color: colors.error,
  },
  separator: {
    fontSize: 48,
    fontWeight: 'bold',
    color: colors.text,
  },
  stepButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.primary,
  },
  pressed: {
    opacity: 0.8,
  },
  cancel: {
    alignItems: 'center',
    paddingVertical: 8,
  },
  cancelText: {
    fontSize: 16,
    fontWeight: '600',
    color: colors.primary,
  },
});
