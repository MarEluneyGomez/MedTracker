import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import Screen from '../components/Screen';
import TextField from '../components/TextField';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/api';
import { deactivateAccount } from '../services/auth';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';

const CONSEQUENCES = {
  PATIENT:
    'Vas a perder el acceso a tu cuenta, se van a cancelar tus citas y se van a quitar tus cuidadores. Tampoco vas a poder crear otra cuenta con el mismo email.',
  CAREGIVER:
    'Vas a perder el acceso a tu cuenta y se van a quitar los pacientes a tu cargo. Tampoco vas a poder crear otra cuenta con el mismo email.',
};

export default function DeactivateAccountScreen() {
  const { user, signOut } = useAuth();
  const [password, setPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  if (!user || user.role === 'DOCTOR') {
    return null;
  }

  function confirmDeactivation() {
    if (!password) {
      setPasswordError('Escribí tu contraseña.');
      return;
    }
    setPasswordError(null);
    Alert.alert('Eliminar la cuenta', '¿Seguro? No se puede deshacer.', [
      { text: 'Volver', style: 'cancel' },
      { text: 'Eliminar', style: 'destructive', onPress: handleDeactivate },
    ]);
  }

  async function handleDeactivate() {
    setError(null);
    setSaving(true);
    try {
      await deactivateAccount(password);
      await signOut();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo eliminar la cuenta.');
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
        <Text style={styles.title}>Eliminar mi cuenta</Text>
        <View style={styles.warning}>
          <Ionicons name="warning-outline" size={24} color={colors.error} />
          <Text style={styles.warningText}>
            Esto no se puede deshacer. {CONSEQUENCES[user.role]}
          </Text>
        </View>
        <TextField
          label="Contraseña"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          error={passwordError ?? undefined}
        />
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <AppButton
          title="Eliminar mi cuenta"
          variant="danger"
          onPress={confirmDeactivation}
          loading={saving}
        />
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
  warning: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    padding: 14,
    borderRadius: 12,
    backgroundColor: colors.errorBackground,
  },
  warningText: {
    flex: 1,
    fontSize: 16,
    color: colors.text,
  },
  error: fieldError,
});
