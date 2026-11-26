import { useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import type { DoctorPatientsStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { getMedicalHistory } from '../services/medicalHistory';
import { colors } from '../theme/colors';
import type { MedicalHistory } from '../types/api';
import { formatDate } from '../utils/dates';

export default function MedicalHistoryScreen() {
  const { user } = useAuth();
  const navigation = useNavigation<NativeStackNavigationProp<DoctorPatientsStackParamList>>();
  const route = useRoute();
  const patient = route.params as { patientId: string; patientName: string } | undefined;
  const patientId = patient?.patientId ?? user?.id;
  const [records, setRecords] = useState<MedicalHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useFlashScrollIndicators<FlatList<MedicalHistory>>(!loading);

  async function load() {
    if (!patientId) {
      return;
    }
    try {
      const history = await getMedicalHistory(patientId);
      setRecords([...history].sort((a, b) => b.recordDate.localeCompare(a.recordDate)));
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo cargar el historial médico.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useFocusEffect(
    useCallback(() => {
      load();
    }, [patientId]),
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
      <Screen hasHeader>
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen hasHeader>
        <View style={styles.center}>
          <Text style={styles.message}>{error}</Text>
          <AppButton title="Reintentar" onPress={handleRetry} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen hasHeader>
      <FlatList
        ref={listRef}
        data={records}
        persistentScrollbar
        keyExtractor={(record) => record.id}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View style={styles.header}>
            <Text style={styles.title}>{patient?.patientName ?? 'Historial médico'}</Text>
            {patient ? (
              <AppButton
                title="Agregar registro"
                onPress={() => navigation.navigate('MedicalHistoryForm', patient)}
              />
            ) : null}
          </View>
        }
        ListEmptyComponent={
          <Text style={styles.message}>
            {patient
              ? 'Todavía no hay registros en el historial de este paciente.'
              : 'Todavía no tenés registros en tu historial médico. Los carga tu médico después de una cita.'}
          </Text>
        }
        renderItem={({ item }) => (
          <View style={styles.card}>
            <Text style={styles.date}>{formatDate(item.recordDate)}</Text>
            <Text style={styles.diagnosis}>{item.diagnosis}</Text>
            {item.notes ? <Text style={styles.notes}>{item.notes}</Text> : null}
          </View>
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
  header: {
    gap: 12,
  },
  title: {
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
    padding: 16,
    gap: 4,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  date: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  diagnosis: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  notes: {
    fontSize: 16,
    color: colors.text,
  },
});
