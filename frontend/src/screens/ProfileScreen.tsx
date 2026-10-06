import { StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { colors } from '../theme/colors';
import { UserRole } from '../types/api';

const ROLE_LABELS: Record<UserRole, string> = {
  PATIENT: 'Paciente',
  CAREGIVER: 'Tutor',
  DOCTOR: 'Doctor',
};

export default function ProfileScreen() {
  const { user, signOut } = useAuth();

  if (!user) {
    return null;
  }

  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.title}>{user?.name}</Text>
        <Text style={styles.subtitle}>Rol: {ROLE_LABELS[user.role]}</Text>
        <Text style={styles.subtitle}>Mail: {user?.email}</Text>
        <View style={styles.actions}>
          <AppButton title="Cerrar sesión" onPress={signOut} />
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
    fontSize: 24,
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
  },
});
