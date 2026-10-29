import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import {
  COMMON_FREQUENCIES,
  MAX_TIMES_PER_DAY,
  MIN_TIMES_PER_DAY,
  formatFrequency,
  isSameFrequency,
  type Frequency,
  type FrequencyUnit,
} from '../utils/frequency';
import ChipSelector from './ChipSelector';
import TextField from './TextField';

const TIMES_PER_DAY = 'Varias veces por día';
const OTHER = 'Otra';

const UNIT_OPTIONS: { label: string; unit: FrequencyUnit }[] = [
  { label: 'Horas', unit: 'hours' },
  { label: 'Días', unit: 'days' },
  { label: 'Semanas', unit: 'weeks' },
];

const TIMES_OPTIONS = Array.from(
  { length: MAX_TIMES_PER_DAY - MIN_TIMES_PER_DAY + 1 },
  (_, index) => String(MIN_TIMES_PER_DAY + index),
);

type Mode = 'common' | 'timesPerDay' | 'custom';

type FrequencyPickerProps = {
  value: Frequency | null;
  onChange: (frequency: Frequency | null) => void;
  error?: string;
};

function initialMode(value: Frequency | null): Mode {
  if (!value || COMMON_FREQUENCIES.some((common) => isSameFrequency(common, value))) {
    return 'common';
  }
  return value.kind === 'timesPerDay' ? 'timesPerDay' : 'custom';
}

export default function FrequencyPicker({ value, onChange, error }: FrequencyPickerProps) {
  const [mode, setMode] = useState<Mode>(initialMode(value));
  const startsCustom = mode === 'custom' && value?.kind === 'interval';
  const [customEvery, setCustomEvery] = useState(startsCustom ? String(value.every) : '');
  const [customUnit, setCustomUnit] = useState<FrequencyUnit>(startsCustom ? value.unit : 'hours');

  function emitCustom(everyText: string, unit: FrequencyUnit) {
    const every = Number(everyText);
    onChange(Number.isInteger(every) && every >= 1 ? { kind: 'interval', every, unit } : null);
  }

  function handleChip(label: string) {
    if (label === OTHER) {
      setMode('custom');
      emitCustom(customEvery, customUnit);
      return;
    }
    if (label === TIMES_PER_DAY) {
      setMode('timesPerDay');
      onChange(value?.kind === 'timesPerDay' ? value : null);
      return;
    }
    setMode('common');
    const preset = COMMON_FREQUENCIES.find((frequency) => formatFrequency(frequency) === label);
    onChange(preset ?? null);
  }

  function handleUnit(label: string) {
    const option = UNIT_OPTIONS.find((o) => o.label === label);
    if (option) {
      setCustomUnit(option.unit);
      emitCustom(customEvery, option.unit);
    }
  }

  const labels = [...COMMON_FREQUENCIES.map(formatFrequency), TIMES_PER_DAY, OTHER];
  const modeLabels: Record<Mode, string | null> = {
    common: value ? formatFrequency(value) : null,
    timesPerDay: TIMES_PER_DAY,
    custom: OTHER,
  };
  const unitLabel = UNIT_OPTIONS.find((o) => o.unit === customUnit)?.label ?? null;
  const timesLabel = value?.kind === 'timesPerDay' ? String(value.times) : null;

  return (
    <View style={styles.container}>
      <ChipSelector
        label="¿Cada cuánto se toma?"
        options={labels}
        value={modeLabels[mode]}
        onChange={handleChip}
      />
      {mode === 'timesPerDay' ? (
        <ChipSelector
          label="¿Cuántas veces por día?"
          options={TIMES_OPTIONS}
          value={timesLabel}
          onChange={(times) => onChange({ kind: 'timesPerDay', times: Number(times) })}
        />
      ) : null}
      {mode === 'custom' ? (
        <View style={styles.custom}>
          <TextField
            label="Cada"
            placeholder="Por ejemplo: 48"
            keyboardType="number-pad"
            value={customEvery}
            onChangeText={(text) => {
              setCustomEvery(text);
              emitCustom(text, customUnit);
            }}
          />
          <ChipSelector
            label="Unidad"
            options={UNIT_OPTIONS.map((o) => o.label)}
            value={unitLabel}
            onChange={handleUnit}
          />
        </View>
      ) : null}
      {value ? <Text style={styles.summary}>Se va a guardar: {formatFrequency(value)}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  custom: {
    gap: 12,
  },
  summary: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  error: fieldError,
});
