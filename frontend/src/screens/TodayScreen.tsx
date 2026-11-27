import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import DoseCard from '../components/DoseCard';
import EmptyState from '../components/EmptyState';
import Screen from '../components/Screen';
import { useActivePatient } from '../context/ActivePatientContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import { ApiError } from '../services/api';
import { changeDose, getTodayDoses, syncPendingDoseActions } from '../services/doses';
import { subscribeToDoseChanges } from '../services/doseEvents';
import { syncDoseNotifications } from '../services/doseNotifications';
import {
  applyPendingDoseActions,
  loadPendingDoseActions,
  type DoseAction,
} from '../services/pendingDoseActions';
import { getMedications } from '../services/medications';
import { getReminders } from '../services/reminders';
import { getTreatmentsByUser } from '../services/treatments';
import { colors } from '../theme/colors';
import type { Dose } from '../types/api';
import { formatLongDate } from '../utils/dates';
import { buildDoseDetails, type DoseDetails } from '../utils/doseDetails';

export default function TodayScreen() {
  const patient = useActivePatient();
  const [doses, setDoses] = useState<Dose[]>([]);
  const [details, setDetails] = useState<Record<string, DoseDetails>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());
  const listRef = useFlashScrollIndicators<FlatList<Dose>>(!loading);

  async function load() {
    if (!patient.id) {
      return;
    }
    try {
      await syncPendingDoseActions();
      const [todayDoses, reminders, treatments, medications] = await Promise.all([
        getTodayDoses(patient.id),
        getReminders(),
        getTreatmentsByUser(patient.id),
        getMedications(),
      ]);
      const pending = await loadPendingDoseActions();
      setDoses(applyPendingDoseActions(todayDoses, pending));
      setPendingIds(new Set(pending.map((action) => action.doseId)));
      setDetails(buildDoseDetails(reminders, treatments, medications));
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
    }, [patient.id]),
  );

  useEffect(() => subscribeToDoseChanges(load), [patient.id]);

  function handleRefresh() {
    setRefreshing(true);
    load();
  }

  function handleRetry() {
    setLoading(true);
    load();
  }

  async function handleDoseAction(dose: Dose, action: DoseAction) {
    setBusyId(dose.id);
    try {
      const change = await changeDose(dose, action);
      setDoses((current) => current.map((item) => (item.id === dose.id ? change.dose : item)));
      setPendingIds((current) => {
        const next = new Set(current);
        if (change.isPendingSync) {
          next.add(dose.id);
        } else {
          next.delete(dose.id);
        }
        return next;
      });
      if (patient.isOwn && patient.id) {
        syncDoseNotifications(patient.id);
      }
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
                  {patient.isOwn ? 'Tomaste' : 'Tomó'} {taken} de {doses.length}
                </Text>
                <View style={styles.progressTrack}>
                  <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
                </View>
              </View>
            ) : null}
          </View>
        }
        ListEmptyComponent={
          <EmptyState
            icon="today-outline"
            message={`${patient.isOwn ? 'No tenés' : 'No tiene'} tomas para hoy. Los horarios se cargan desde Tratamientos.`}
          />
        }
        renderItem={({ item }) => {
          const doseDetails = details[item.reminderId];
          return (
            <DoseCard
              dose={item}
              medicationName={doseDetails?.medicationName ?? 'Medicamento'}
              dosage={doseDetails?.dosage ?? ''}
              busy={busyId === item.id}
              isPendingSync={pendingIds.has(item.id)}
              onConfirm={() => handleDoseAction(item, 'confirm')}
              onSkip={() => handleDoseAction(item, 'skip')}
              onUndo={() => handleDoseAction(item, 'undo')}
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
    flexGrow: 1,
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
