import { useState } from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import AppButton from '../components/AppButton';
import Logo from '../components/Logo';
import RoleSelector from '../components/RoleSelector';
import Screen from '../components/Screen';
import TextField from '../components/TextField';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import { ApiError } from '../services/api';
import { colors } from '../theme/colors';
import { fieldError } from '../theme/typography';
import type { UserRole } from '../types/api';

type FieldErrors = {
  name?: string;
  email?: string;
  confirmEmail?: string; // nuevo
  password?: string;
  confirmPassword?: string; // nuevo
};

export default function RegisterScreen() {
  const { signUp } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [confirmEmail, setConfirmEmail] = useState(''); // nuevo
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState(''); // nuevo
  const [role, setRole] = useState<UserRole>('PATIENT');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const scrollRef = useFlashScrollIndicators<ScrollView>();

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!name.trim()) {
      errors.name = 'Ingresá tu nombre.';
    }
    if (!email.includes('@')) {
      errors.email = 'Ingresá un email válido.';
    }
    if (confirmEmail.trim().toLowerCase() !== email.trim().toLowerCase()) {
      errors.confirmEmail = 'Los emails no coinciden.';
    }
    if (password.length < 6) {
      errors.password = 'La contraseña tiene que tener al menos 6 caracteres.';
    }
    if (confirmPassword !== password) {
      errors.confirmPassword = 'Las contraseñas no coinciden.';
    }
    return errors;
  }

  async function handleSubmit() {
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await signUp({ name, email, password, role });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Ocurrió un error inesperado.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen hasHeader>
      <ScrollView
        ref={scrollRef}
        contentContainerStyle={styles.container}
        keyboardShouldPersistTaps="handled"
        persistentScrollbar
      >
        <Logo />
        <Text style={styles.title}>Crear cuenta</Text>
        <TextField label="Nombre" value={name} onChangeText={setName} error={fieldErrors.name} />
        <TextField
          label="Email"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
          error={fieldErrors.email}
        />
        <TextField
          label="Repetí el email"
          value={confirmEmail}
          onChangeText={setConfirmEmail}
          keyboardType="email-address"
          autoCapitalize="none"
          error={fieldErrors.confirmEmail}
        />
        <TextField
          label="Contraseña"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          error={fieldErrors.password}
        />
        <TextField
          label="Repetí la contraseña"
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          secureTextEntry
          autoCapitalize="none"
          error={fieldErrors.confirmPassword}
        />
        <RoleSelector value={role} onChange={setRole} />
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <AppButton title="Crear cuenta" onPress={handleSubmit} loading={loading} />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    gap: 16,
    paddingVertical: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  error: fieldError,
});
