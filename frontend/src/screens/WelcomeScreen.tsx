import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import AppButton from '../components/AppButton';
import Screen from '../components/Screen';
import { ApiError } from '../services/api';
import { useAuth } from '../context/AuthContext';

export default function WelcomeScreen() {
  const { signIn } = useAuth();

  // Provisorio: accesos rápidos con los usuarios de prueba hasta que exista
  // la pantalla de login
  async function signInAs(email: string) {
    try {
      await signIn(email, '1234');
    } catch (error) {
      if (error instanceof ApiError) {
        console.log('Login falló:', error.status, error.message);
      }
    }
  }

  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.title}>MedTracker</Text>
        <Text style={styles.subtitle}>Tu medicación, siempre a tiempo</Text>
        <View style={styles.actions}>
          <AppButton title="Entrar como paciente" onPress={() => signInAs('paciente@demo.com')} />
          <AppButton title="Entrar como tutor" onPress={() => signInAs('tutor@demo.com')} />
          <AppButton title="Entrar como médico" onPress={() => signInAs('medico@demo.com')} />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: colors.text,
  },
  subtitle: {
    marginTop: 8,
    fontSize: 16,
    color: colors.textSecondary,
  },
  actions: {
    marginTop: 32,
    gap: 12,
  },
});
