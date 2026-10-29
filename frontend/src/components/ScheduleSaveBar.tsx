import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import AppButton from './AppButton';

type ScheduleSaveBarProps = {
  isSaved: boolean;
  isNew: boolean;
  saving: boolean;
  error: string | null;
  onSave: () => void;
};

// Cuando lo que se ve ya está guardado se muestra una confirmación en lugar
// del botón, para que quede claro que no hay cambios pendientes
export default function ScheduleSaveBar({
  isSaved,
  isNew,
  saving,
  error,
  onSave,
}: ScheduleSaveBarProps) {
  return (
    <View style={styles.container}>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {isSaved ? (
        <View style={styles.saved}>
          <Ionicons name="checkmark-circle" size={24} color={colors.success} />
          <Text style={styles.savedText}>Horarios guardados</Text>
        </View>
      ) : (
        <AppButton
          title={isNew ? 'Crear horarios' : 'Guardar cambios'}
          onPress={onSave}
          loading={saving}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  error: fieldError,
  saved: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 14,
    borderRadius: 12,
    backgroundColor: colors.successBackground,
  },
  savedText: {
    fontSize: 16,
    fontWeight: '600',
    color: colors.success,
  },
});
