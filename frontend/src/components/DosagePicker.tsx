import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import {
  DOSAGE_UNITS,
  formatAmount,
  formatDosage,
  parseAmount,
  type Dosage,
  type DosageUnit,
} from '../utils/dosage';
import ChipSelector from './ChipSelector';
import TextField from './TextField';

const UNITS = Object.keys(DOSAGE_UNITS) as DosageUnit[];

type DosagePickerProps = {
  value: Dosage | null;
  onChange: (dosage: Dosage | null) => void;
  error?: string;
};

export default function DosagePicker({ value, onChange, error }: DosagePickerProps) {
  const [amountText, setAmountText] = useState(value ? formatAmount(value.amount) : '');
  const [unit, setUnit] = useState<DosageUnit | null>(value?.unit ?? null);

  function emit(text: string, selectedUnit: DosageUnit | null) {
    const amount = parseAmount(text);
    onChange(amount !== null && selectedUnit ? { amount, unit: selectedUnit } : null);
  }

  function handleUnit(label: string) {
    const selected = UNITS.find((u) => DOSAGE_UNITS[u].singular === label) ?? null;
    setUnit(selected);
    emit(amountText, selected);
  }

  return (
    <View style={styles.container}>
      <TextField
        label="Dosis: ¿cuánto se toma cada vez?"
        placeholder="Por ejemplo: 400 o 1"
        keyboardType="decimal-pad"
        value={amountText}
        onChangeText={(text) => {
          setAmountText(text);
          emit(text, unit);
        }}
      />
      <ChipSelector
        label="Unidad"
        options={UNITS.map((u) => DOSAGE_UNITS[u].singular)}
        value={unit ? DOSAGE_UNITS[unit].singular : null}
        onChange={handleUnit}
      />
      {value ? <Text style={styles.summary}>Se va a guardar: {formatDosage(value)}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  summary: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  error: fieldError,
});
