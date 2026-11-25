import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import AppButton from '../components/AppButton';
import DateField from '../components/DateField';
import Screen from '../components/Screen';
import TextField from '../components/TextField';
import type { DoctorPatientsStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { createMedicalHistory } from '../services/medicalHistory';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import { isFutureDay, toStartOfDay } from '../utils/dates';

const MAX_DIAGNOSIS_LENGTH = 255;
const MAX_NOTES_LENGTH = 5000;

type FieldErrors = {
  recordDate?: string;
  diagnosis?: string;
  notes?: string;
};

export default function MedicalHistoryFormScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<DoctorPatientsStackParamList>>();
  const { params } = useRoute<RouteProp<DoctorPatientsStackParamList, 'MedicalHistoryForm'>>();
  const [recordDate, setRecordDate] = useState(new Date());
  const [diagnosis, setDiagnosis] = useState('');
  const [notes, setNotes] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (isFutureDay(recordDate)) {
      errors.recordDate = 'La fecha no puede ser futura.';
    }
    if (!diagnosis.trim()) {
      errors.diagnosis = 'Escribí el diagnóstico.';
    } else if (diagnosis.trim().length > MAX_DIAGNOSIS_LENGTH) {
      errors.diagnosis = `El diagnóstico puede tener hasta ${MAX_DIAGNOSIS_LENGTH} caracteres.`;
    }
    if (notes.trim().length > MAX_NOTES_LENGTH) {
      errors.notes = `Las observaciones pueden tener hasta ${MAX_NOTES_LENGTH} caracteres.`;
    }
    return errors;
  }

  async function handleSave() {
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await createMedicalHistory({
        patientId: params.patientId,
        diagnosis,
        notes,
        recordDate: toStartOfDay(recordDate),
      });
      navigation.goBack();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo guardar el registro.');
      setSaving(false);
    }
  }

  return (
    <Screen hasHeader>
      <ScrollView
        contentContainerStyle={styles.container}
        keyboardShouldPersistTaps="handled"
        persistentScrollbar
      >
        <Text style={styles.title}>Nuevo registro</Text>
        <Text style={styles.subtitle}>{params.patientName}</Text>
        <DateField
          label="Fecha"
          value={recordDate}
          onChange={setRecordDate}
          error={fieldErrors.recordDate}
        />
        <TextField
          label="Diagnóstico"
          value={diagnosis}
          onChangeText={setDiagnosis}
          autoCapitalize="sentences"
          error={fieldErrors.diagnosis}
        />
        <TextField
          label="Observaciones (opcional)"
          value={notes}
          onChangeText={setNotes}
          autoCapitalize="sentences"
          multiline
          error={fieldErrors.notes}
        />
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <AppButton title="Guardar registro" onPress={handleSave} loading={saving} />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
    paddingBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  subtitle: {
    marginTop: -8,
    fontSize: 16,
    color: colors.textSecondary,
  },
  error: fieldError,
});
