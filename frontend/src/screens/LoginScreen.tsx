import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import Logo from '../components/Logo';
import Screen from '../components/Screen';
import TextField from '../components/TextField';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/api';
import { colors } from '../theme/colors';

export default function LoginScreen() {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit() {
    if (!email.trim() || !password) {
      setError('Completá el email y la contraseña.');
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await signIn(email, password);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Ocurrió un error inesperado.');
    } finally {
      setLoading(false);
    }
  }

  // Acceso rápido con los usuarios de prueba, solo en desarrollo
  async function signInAs(demoEmail: string) {
    setError(null);
    try {
      await signIn(demoEmail, '1234');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Ocurrió un error inesperado.');
    }
  }

  return (
    <Screen>
      <View style={styles.container}>
        <Logo />
        <Text style={styles.title}>Iniciar sesión</Text>
        <TextField
          label="Email"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
        />
        <TextField
          label="Contraseña"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
        />
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <AppButton title="Ingresar" onPress={handleSubmit} loading={loading} />
        {__DEV__ ? (
          <View style={styles.devAccess}>
            <Text style={styles.devTitle}>Acceso rápido (solo desarrollo)</Text>
            <AppButton title="Entrar como paciente" onPress={() => signInAs('paciente@demo.com')} />
            <AppButton title="Entrar como tutor" onPress={() => signInAs('tutor@demo.com')} />
            <AppButton title="Entrar como médico" onPress={() => signInAs('medico@demo.com')} />
          </View>
        ) : null}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    gap: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  error: {
    fontSize: 14,
    color: colors.error,
  },
  devAccess: {
    marginTop: 24,
    gap: 12,
  },
  devTitle: {
    fontSize: 14,
    color: colors.textSecondary,
  },
});
