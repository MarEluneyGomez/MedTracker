import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import type { CaregiverStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { getCaregiverPatients } from '../services/caregiverLinks';
import { colors } from '../theme/colors';
import type { UserSummary } from '../types/api';

export default function PatientsScreen() {
  const { user } = useAuth();
  const navigation = useNavigation<NativeStackNavigationProp<CaregiverStackParamList>>();
  const [patients, setPatients] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useFlashScrollIndicators<FlatList<UserSummary>>(!loading);

  async function load() {
    if (!user) {
      return;
    }
    try {
      const linked = await getCaregiverPatients(user.id);
      setPatients([...linked].sort((a, b) => a.name.localeCompare(b.name)));
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudieron cargar tus pacientes.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useFocusEffect(
    useCallback(() => {
      load();
    }, [user]),
  );

  function handleRefresh() {
    setRefreshing(true);
    load();
  }

  function handleRetry() {
    setLoading(true);
    load();
  }

  if (loading) {
    return (
      <Screen>
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <View style={styles.center}>
          <Text style={styles.message}>{error}</Text>
          <AppButton title="Reintentar" onPress={handleRetry} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen>
      <FlatList
        ref={listRef}
        data={patients}
        persistentScrollbar
        keyExtractor={(patient) => patient.id}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        contentContainerStyle={styles.list}
        ListHeaderComponent={<Text style={styles.title}>Mis pacientes</Text>}
        ListEmptyComponent={
          <Text style={styles.message}>
            Todavía no tenés pacientes a cargo. Un paciente te agrega como cuidador desde su cuenta.
          </Text>
        }
        renderItem={({ item }) => (
          <Pressable
            style={styles.card}
            onPress={() =>
              navigation.navigate('PatientDetail', { patientId: item.id, patientName: item.name })
            }
            accessibilityRole="button"
            accessibilityHint="Abre las tomas, los tratamientos y el historial del paciente"
          >
            <View style={styles.cardText}>
              <Text style={styles.name}>{item.name}</Text>
              <Text style={styles.email}>{item.email}</Text>
            </View>
            <Ionicons name="chevron-forward" size={24} color={colors.primary} />
          </Pressable>
        )}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    justifyContent: 'center',
    gap: 16,
  },
  list: {
    gap: 12,
    paddingBottom: 24,
  },
  title: {
    marginTop: 16,
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  cardText: {
    flex: 1,
    gap: 2,
  },
  name: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  email: {
    fontSize: 15,
    color: colors.textSecondary,
  },
});
