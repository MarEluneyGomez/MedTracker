import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import DoseCard from '../components/DoseCard';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import { ApiError } from '../services/api';
import { confirmDose, getTodayDoses, skipDose, undoDose } from '../services/doses';
import { getMedications } from '../services/medications';
import { getReminders } from '../services/reminders';
import { getTreatmentsByUser } from '../services/treatments';
import { colors } from '../theme/colors';
import type { Dose, Medication, Reminder, Treatment } from '../types/api';
import { formatLongDate } from '../utils/dates';

type DoseDetails = {
  medicationName: string;
  dosage: string;
};

// La toma solo trae su recordatorio: el medicamento y la dosis salen del tratamiento
function buildDetails(
  reminders: Reminder[],
  treatments: Treatment[],
  medications: Medication[],
): Record<string, DoseDetails> {
  const treatmentsById = new Map(treatments.map((treatment) => [treatment.id, treatment]));
  const medicationsById = new Map(medications.map((medication) => [medication.id, medication]));
  const details: Record<string, DoseDetails> = {};
  for (const reminder of reminders) {
    const treatment = treatmentsById.get(reminder.treatmentId);
    if (treatment) {
      details[reminder.id] = {
        medicationName: medicationsById.get(treatment.medicationId)?.name ?? 'Medicamento',
        dosage: treatment.dosage,
      };
    }
  }
  return details;
}

export default function TodayScreen() {
  const { user } = useAuth();
  const [doses, setDoses] = useState<Dose[]>([]);
  const [details, setDetails] = useState<Record<string, DoseDetails>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const listRef = useFlashScrollIndicators<FlatList<Dose>>(!loading);

  async function load() {
    if (!user) {
      return;
    }
    try {
      const [todayDoses, reminders, treatments, medications] = await Promise.all([
        getTodayDoses(user.id),
        getReminders(),
        getTreatmentsByUser(user.id),
        getMedications(),
      ]);
      setDoses(todayDoses);
      setDetails(buildDetails(reminders, treatments, medications));
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudieron cargar las tomas de hoy.');
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

  async function changeDose(id: string, action: (id: string) => Promise<Dose>) {
    setBusyId(id);
    try {
      const updated = await action(id);
      setDoses((current) => current.map((dose) => (dose.id === updated.id ? updated : dose)));
    } catch (e) {
      Alert.alert('No se pudo guardar', e instanceof ApiError ? e.message : 'Intentá de nuevo.');
      await load();
    } finally {
      setBusyId(null);
    }
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

  const taken = doses.filter((dose) => dose.status === 'CONFIRMED').length;
  const progress = doses.length > 0 ? taken / doses.length : 0;

  return (
    <Screen>
      <FlatList
        ref={listRef}
        data={doses}
        persistentScrollbar
        keyExtractor={(dose) => dose.id}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View style={styles.header}>
            <Text style={styles.title}>Hoy</Text>
            <Text style={styles.date}>{formatLongDate(new Date())}</Text>
            {doses.length > 0 ? (
              <View style={styles.summary}>
                <Text style={styles.summaryText}>
                  Tomaste {taken} de {doses.length}
                </Text>
                <View style={styles.progressTrack}>
                  <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
                </View>
              </View>
            ) : null}
          </View>
        }
        ListEmptyComponent={
          <Text style={styles.message}>
            No tenés tomas para hoy. Los horarios se cargan desde Tratamientos.
          </Text>
        }
        renderItem={({ item }) => {
          const doseDetails = details[item.reminderId];
          return (
            <DoseCard
              dose={item}
              medicationName={doseDetails?.medicationName ?? 'Medicamento'}
              dosage={doseDetails?.dosage ?? ''}
              busy={busyId === item.id}
              onConfirm={() => changeDose(item.id, confirmDose)}
              onSkip={() => changeDose(item.id, skipDose)}
              onUndo={() => changeDose(item.id, undoDose)}
            />
          );
        }}
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
    marginTop: 16,
    gap: 4,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  date: {
    fontSize: 16,
    color: colors.textSecondary,
  },
  summary: {
    marginTop: 12,
    gap: 8,
  },
  summaryText: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  progressTrack: {
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.surfaceTinted,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: colors.success,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
});
