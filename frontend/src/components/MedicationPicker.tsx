import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { NewMedication } from '../services/medications';
import { colors } from '../theme/colors';
import { fieldError, fieldLabel } from '../theme/typography';
import type { Medication } from '../types/api';
import { normalizeForSearch } from '../utils/text';
import AppButton from './AppButton';
import ChipSelector from './ChipSelector';
import TextField from './TextField';

const ADMINISTRATION_FORMS = ['Oral', 'Inyectable', 'Tópica', 'Inhalada', 'Gotas'];

type MedicationPickerProps = {
  medications: Medication[];
  selected: Medication | null;
  onSelect: (medication: Medication | null) => void;
  onCreate: (data: NewMedication) => Promise<void>;
  error?: string;
  locked?: boolean;
};

export default function MedicationPicker({
  medications,
  selected,
  onSelect,
  onCreate,
  error,
  locked = false,
}: MedicationPickerProps) {
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const [administrationForm, setAdministrationForm] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  async function handleCreate() {
    if (!administrationForm) {
      setCreateError('Elegí cómo se toma el medicamento.');
      return;
    }
    setCreateError(null);
    setSaving(true);
    try {
      await onCreate({ name: query, administrationForm });
      setQuery('');
      setCreating(false);
      setAdministrationForm(null);
    } catch {
      setCreateError('No se pudo agregar el medicamento. Intentá de nuevo.');
    } finally {
      setSaving(false);
    }
  }

  if (selected) {
    return (
      <View>
        <Text style={styles.label}>Medicamento</Text>
        <View style={styles.selected}>
          <View>
            <Text style={styles.name}>{selected.name}</Text>
            <Text style={styles.form}>{selected.administrationForm}</Text>
          </View>
          {!locked ? (
            <Pressable onPress={() => onSelect(null)} accessibilityRole="button">
              <Text style={styles.change}>Cambiar</Text>
            </Pressable>
          ) : null}
        </View>
        {locked ? (
          <Text style={styles.hint}>
            El medicamento no se puede cambiar. Para otro medicamento, creá un tratamiento nuevo.
          </Text>
        ) : null}
      </View>
    );
  }

  const search = normalizeForSearch(query.trim());
  const results = search
    ? medications.filter((m) => normalizeForSearch(m.name).includes(search)).slice(0, 5)
    : [];

  return (
    <View>
      <TextField
        label="Buscar medicamento"
        icon="search"
        placeholder="Escribí el nombre, por ejemplo Ibuprofeno"
        value={query}
        onChangeText={(text) => {
          setQuery(text);
          setCreating(false);
        }}
        autoCapitalize="none"
        error={error}
      />
      {results.map((medication) => (
        <Pressable
          key={medication.id}
          style={styles.result}
          onPress={() => onSelect(medication)}
          accessibilityRole="button"
        >
          <Text style={styles.name}>{medication.name}</Text>
          <Text style={styles.form}>{medication.administrationForm}</Text>
        </Pressable>
      ))}

      {search && results.length === 0 && !creating ? (
        <Pressable
          style={styles.result}
          onPress={() => setCreating(true)}
          accessibilityRole="button"
        >
          <Text style={styles.change}>Agregar “{query.trim()}” al catálogo</Text>
        </Pressable>
      ) : null}

      {creating ? (
        <View style={styles.createBox}>
          <ChipSelector
            label="¿Cómo se toma?"
            options={ADMINISTRATION_FORMS}
            value={administrationForm}
            onChange={setAdministrationForm}
          />
          {createError ? <Text style={styles.error}>{createError}</Text> : null}
          <AppButton title="Guardar medicamento" onPress={handleCreate} loading={saving} />
        </View>
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
  form: {
    marginTop: 2,
    fontSize: 14,
    color: colors.textSecondary,
  },
  change: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.primary,
  },
  hint: {
    marginTop: 6,
    fontSize: 15,
    color: colors.textSecondary,
  },
  createBox: {
    marginTop: 12,
    gap: 12,
  },
  error: fieldError,
});
