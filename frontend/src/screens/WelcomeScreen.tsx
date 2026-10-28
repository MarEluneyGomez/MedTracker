import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';
import Screen from '../components/Screen';
import Logo from '../components/Logo';

export default function WelcomeScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();

  return (
    <Screen>
      <View style={styles.container}>
        <Logo />
        <Text style={styles.subtitle}>Tu medicación, siempre a tiempo</Text>
        <View style={styles.actions}>
          <AppButton title="Iniciar Sesión" onPress={() => navigation.navigate('Login')} />
          <AppButton title="Crear cuenta" onPress={() => navigation.navigate('Register')} />
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
