import { StyleSheet, Text, View } from 'react-native';
import { fieldError, fieldLabel } from '../theme/typography';
import type { WeekDay } from '../types/api';
import { WEEK_DAYS } from '../utils/reminders';
import Chip from './Chip';

type DaySelectorProps = {
  label: string;
  value: WeekDay[];
  onChange: (days: WeekDay[]) => void;
  error?: string;
};

export default function DaySelector({ label, value, onChange, error }: DaySelectorProps) {
  function toggle(day: WeekDay) {
    onChange(value.includes(day) ? value.filter((chosen) => chosen !== day) : [...value, day]);
  }

  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.options}>
        {WEEK_DAYS.map((option) => (
          <Chip
            key={option.day}
            label={option.label}
            chosen={value.includes(option.day)}
            onPress={() => toggle(option.day)}
          />
        ))}
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  label: fieldLabel,
  options: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  error: { ...fieldError, marginTop: 4 },
});
