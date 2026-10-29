import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldError, fieldLabel } from '../theme/typography';
import type { Frequency, IntervalFrequency } from '../utils/frequency';
import {
  WEEK_DAYS,
  formatDays,
  formatTime,
  freeTimesFor,
  isWeekly,
  scheduleModeOf,
  scheduleTargets,
  type Schedule,
  type TimeOfDay,
} from '../utils/reminders';
import ChipSelector from './ChipSelector';
import TimeField from './TimeField';

type SchedulePickerProps = {
  frequency: Frequency | null;
  value: Schedule;
  onChange: (schedule: Schedule) => void;
  error?: string;
};

// Muestra los campos de horario que corresponden a la frecuencia. No guarda
// nada: cada pantalla decide cuándo guardar.
export default function SchedulePicker({ frequency, value, onChange, error }: SchedulePickerProps) {
  if (!frequency) {
    return null;
  }
  if (scheduleModeOf(frequency) === 'manual') {
    return (
      <Text style={styles.notice}>
        Esta frecuencia no se puede programar automáticamente. Después de guardar, los horarios se
        cargan a mano en la pantalla Horarios.
      </Text>
    );
  }
  return (
    <View style={styles.container}>
      {frequency.kind === 'timesPerDay' ? (
        <FreeTimesFields timesPerDay={frequency.times} value={value} onChange={onChange} />
      ) : (
        <FixedTimesFields frequency={frequency} value={value} onChange={onChange} />
      )}
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

type FreeTimesFieldsProps = {
  timesPerDay: number;
  value: Schedule;
  onChange: (schedule: Schedule) => void;
};

function FreeTimesFields({ timesPerDay, value, onChange }: FreeTimesFieldsProps) {
  const times = freeTimesFor(value, timesPerDay);

  function changeTime(index: number, time: TimeOfDay) {
    onChange({
      ...value,
      freeTimes: times.map((current, position) => (position === index ? time : current)),
    });
  }

  return (
    <>
      {times.map((time, index) => (
        <TimeField
          key={index}
          label={`Toma ${index + 1}`}
          value={time}
          onChange={(changed) => changeTime(index, changed)}
        />
      ))}
    </>
  );
}

type FixedTimesFieldsProps = {
  frequency: IntervalFrequency;
  value: Schedule;
  onChange: (schedule: Schedule) => void;
};

function FixedTimesFields({ frequency, value, onChange }: FixedTimesFieldsProps) {
  const weekly = isWeekly(frequency);
  const targets = scheduleTargets(frequency, value) ?? [];
  const weekDayLabel = WEEK_DAYS.find(({ day }) => day === value.weekDay)?.label ?? null;

  function handleWeekDay(label: string) {
    onChange({
      ...value,
      weekDay: WEEK_DAYS.find((option) => option.label === label)?.day ?? null,
    });
  }

  return (
    <>
      <TimeField
        label="Primera toma del día"
        value={value.firstTime}
        onChange={(firstTime) => onChange({ ...value, firstTime })}
      />
      {weekly ? (
        <ChipSelector
          label="¿Qué día de la semana?"
          options={WEEK_DAYS.map(({ label }) => label)}
          value={weekDayLabel}
          onChange={handleWeekDay}
        />
      ) : null}
      <View>
        <Text style={styles.label}>Horarios</Text>
        <View style={styles.times}>
          {targets.map(({ time, daysOfWeek }) => (
            <View key={formatTime(time)} style={styles.timeRow}>
              <Ionicons name="alarm-outline" size={24} color={colors.primary} />
              <Text style={styles.time}>{formatTime(time)}</Text>
              <Text style={styles.days}>
                {weekly && !value.weekDay ? '' : formatDays(daysOfWeek)}
              </Text>
            </View>
          ))}
        </View>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  label: fieldLabel,
  error: fieldError,
  notice: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  times: {
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  time: {
    fontSize: 22,
    fontWeight: 'bold',
    fontVariant: ['tabular-nums'],
    color: colors.text,
  },
  days: {
    flex: 1,
    textAlign: 'right',
    fontSize: 15,
    color: colors.textSecondary,
  },
});
