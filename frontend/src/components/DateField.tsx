import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Calendar, type DateData } from 'react-native-calendars';
import { colors } from '../theme/colors';
import { fieldError, fieldLabel } from '../theme/typography';
import { formatDate, toDayString, toStartOfDay } from '../utils/dates';
import AppButton from './AppButton';

const calendarTheme = {
  calendarBackground: colors.surface,
  monthTextColor: colors.text,
  textMonthFontSize: 18,
  textMonthFontWeight: 'bold' as const,
  textSectionTitleColor: colors.textSecondary,
  dayTextColor: colors.text,
  textDayFontSize: 16,
  todayTextColor: colors.primary,
  selectedDayBackgroundColor: colors.primary,
  selectedDayTextColor: colors.onPrimary,
  textDisabledColor: colors.borderStrong,
};

type DateFieldProps = {
  label: string;
  value: Date | null;
  onChange: (date: Date) => void;
  minimumDate?: Date;
  error?: string;
};

export default function DateField({ label, value, onChange, minimumDate, error }: DateFieldProps) {
  const [open, setOpen] = useState(false);
  const selectedDay = value ? toDayString(value) : undefined;

  function handleDayPress(day: DateData) {
    onChange(new Date(day.year, day.month - 1, day.day));
    setOpen(false);
  }

  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <Pressable
        style={[styles.box, error ? styles.boxError : null]}
        onPress={() => setOpen(true)}
        accessibilityRole="button"
        accessibilityHint="Abre el calendario"
      >
        <Ionicons name="calendar-outline" size={20} color={colors.textSecondary} />
        <Text style={value ? styles.value : styles.placeholder}>
          {value ? formatDate(toStartOfDay(value)) : 'Elegí una fecha'}
        </Text>
      </Pressable>
      {error ? <Text style={styles.error}>{error}</Text> : null}

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <View style={styles.overlay}>
          <View style={styles.sheet}>
            <Text style={styles.sheetTitle}>{label}</Text>
            <Calendar
              current={selectedDay}
              minDate={minimumDate ? toDayString(minimumDate) : undefined}
              markedDates={selectedDay ? { [selectedDay]: { selected: true } } : undefined}
              onDayPress={handleDayPress}
              theme={calendarTheme}
              renderArrow={(direction) => (
                <Ionicons
                  name={direction === 'left' ? 'chevron-back' : 'chevron-forward'}
                  size={30}
                  color={colors.primary}
                />
              )}
            />
            <AppButton title="Cancelar" onPress={() => setOpen(false)} />
          </View>
        </View>
      </Modal>
    </View>
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
  placeholder: {
    fontSize: 16,
    color: colors.textSecondary,
  },
  error: { ...fieldError, marginTop: 4 },
  overlay: {
    flex: 1,
    justifyContent: 'center',
    padding: 16,
    backgroundColor: colors.overlay,
  },
  sheet: {
    gap: 12,
    padding: 16,
    borderRadius: 16,
    backgroundColor: colors.surface,
  },
  sheetTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
  },
});
