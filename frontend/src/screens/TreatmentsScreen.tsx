import { StyleSheet, Text, View } from 'react-native';
import Screen from '../components/Screen';
import { colors } from '../theme/colors';

// Provisoria
export default function TreatmentsScreen() {
  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.title}>Tratamientos</Text>
        <Text style={styles.subtitle}>Próximamente: tus tratamientos</Text>
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
});
