import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldLabel } from '../theme/typography';
import type { Doctor } from '../types/api';
import { normalizeForSearch } from '../utils/text';
import TextField from './TextField';

const MAX_RESULTS = 5;

type DoctorPickerProps = {
  doctors: Doctor[];
  selected: Doctor | null;
  onSelect: (doctor: Doctor | null) => void;
  error?: string;
};

export default function DoctorPicker({ doctors, selected, onSelect, error }: DoctorPickerProps) {
  const [query, setQuery] = useState('');

  if (selected) {
    return (
      <View>
        <Text style={styles.label}>Médico</Text>
        <View style={styles.selected}>
          <Text style={styles.name}>{selected.name}</Text>
          <Pressable onPress={() => onSelect(null)} accessibilityRole="button">
            <Text style={styles.change}>Cambiar</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  const search = normalizeForSearch(query.trim());
  const results = search
    ? doctors
        .filter((doctor) => normalizeForSearch(doctor.name).includes(search))
        .slice(0, MAX_RESULTS)
    : [];

  return (
    <View>
      <TextField
        label="Buscar médico"
        icon="search"
        placeholder="Escribí el nombre del médico"
        value={query}
        onChangeText={setQuery}
        autoCapitalize="none"
        error={error}
      />
      {results.map((doctor) => (
        <Pressable
          key={doctor.id}
          style={styles.result}
          onPress={() => onSelect(doctor)}
          accessibilityRole="button"
        >
          <Text style={styles.name}>{doctor.name}</Text>
        </Pressable>
      ))}
      {search && results.length === 0 ? (
        <Text style={styles.hint}>No hay médicos con ese nombre.</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  label: fieldLabel,
  selected: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 14,
    borderWidth: 1,
    borderColor: colors.primary,
    borderRadius: 12,
    backgroundColor: colors.surface,
  },
  result: {
    marginTop: 8,
    padding: 14,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    backgroundColor: colors.surface,
  },
  name: {
    fontSize: 16,
    fontWeight: '600',
    color: colors.text,
  },
  change: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.primary,
  },
  hint: {
    marginTop: 8,
    fontSize: 15,
    color: colors.textSecondary,
  },
});
