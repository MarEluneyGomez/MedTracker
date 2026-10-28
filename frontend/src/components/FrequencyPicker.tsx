import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import {
  COMMON_FREQUENCIES,
  formatFrequency,
  type Frequency,
  type FrequencyUnit,
} from '../utils/frequency';
import ChipSelector from './ChipSelector';
import TextField from './TextField';

const OTHER = 'Otra';

const UNIT_OPTIONS: { label: string; unit: FrequencyUnit }[] = [
  { label: 'Horas', unit: 'hours' },
  { label: 'Días', unit: 'days' },
  { label: 'Semanas', unit: 'weeks' },
];

type FrequencyPickerProps = {
  value: Frequency | null;
  onChange: (frequency: Frequency | null) => void;
  error?: string;
};

function isCommonFrequency({ every, unit }: Frequency): boolean {
  return COMMON_FREQUENCIES.some((common) => common.every === every && common.unit === unit);
}

export default function FrequencyPicker({ value, onChange, error }: FrequencyPickerProps) {
  const startsCustom = value !== null && !isCommonFrequency(value);
  const [isCustom, setIsCustom] = useState(startsCustom);
  const [customEvery, setCustomEvery] = useState(startsCustom ? String(value.every) : '');
  const [customUnit, setCustomUnit] = useState<FrequencyUnit>(startsCustom ? value.unit : 'hours');

  function emitCustom(everyText: string, unit: FrequencyUnit) {
    const every = Number(everyText);
    onChange(Number.isInteger(every) && every >= 1 ? { every, unit } : null);
  }

  function handleChip(label: string) {
    if (label === OTHER) {
      setIsCustom(true);
      emitCustom(customEvery, customUnit);
      return;
    }
    setIsCustom(false);
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

  const labels = [...COMMON_FREQUENCIES.map(formatFrequency), OTHER];
  const selectedLabel = isCustom ? OTHER : value ? formatFrequency(value) : null;
  const unitLabel = UNIT_OPTIONS.find((o) => o.unit === customUnit)?.label ?? null;

  return (
    <View style={styles.container}>
      <ChipSelector
        label="¿Cada cuánto se toma?"
        options={labels}
        value={selectedLabel}
        onChange={handleChip}
      />
      {isCustom ? (
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
