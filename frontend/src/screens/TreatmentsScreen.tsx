import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import AppButton from '../components/AppButton';
import Screen from '../components/Screen';
import { useActivePatient } from '../context/ActivePatientContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import { ApiError } from '../services/api';
import { syncDoseNotifications } from '../services/doseNotifications';
import { getMedications } from '../services/medications';
import {
  completeTreatment,
  deleteTreatment,
  getTreatmentsByUser,
  reopenTreatment,
} from '../services/treatments';

import { colors } from '../theme/colors';
import type { Medication, Treatment } from '../types/api';
import { formatDate } from '../utils/dates';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { TreatmentsStackParamList } from '../navigation/types';

export default function TreatmentsScreen() {
  const patient = useActivePatient();
  const navigation = useNavigation<NativeStackNavigationProp<TreatmentsStackParamList>>();
  const [treatments, setTreatments] = useState<Treatment[]>([]);
  const [medications, setMedications] = useState<Record<number, Medication>>({});
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useFlashScrollIndicators<FlatList<Treatment>>(!loading);
  const [busyId, setBusyId] = useState<string | null>(null);

  function hasEnded(treatment: Treatment): boolean {
    return treatment.endDate !== null && new Date(treatment.endDate) < new Date();
  }

  function needsSchedule(treatment: Treatment): boolean {
    return !treatment.completed && !hasEnded(treatment) && treatment.hasActiveReminders === false;
  }

  function treatmentState(treatment: Treatment): string {
    if (treatment.completed) {
      return 'Completado';
    }
    return hasEnded(treatment) ? 'Finalizado' : 'En curso';
  }

  async function load() {
    if (!patient.id) {
      return;
    }
    try {
      const [userTreatments, catalog] = await Promise.all([
        getTreatmentsByUser(patient.id),
        getMedications(),
      ]);
      const byId: Record<number, Medication> = {};
      for (const medication of catalog) {
        byId[medication.id] = medication;
      }
      setTreatments(userTreatments);
      setMedications(byId);
      if (patient.isOwn) {
        syncDoseNotifications(patient.id);
      }
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudieron cargar los tratamientos.');
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

  function handleRefresh() {
    setRefreshing(true);
    load();
  }

  function handleRetry() {
    setLoading(true);
    load();
  }

  function toggle(id: string) {
    setExpandedId(expandedId === id ? null : id);
  }

  async function runAction(id: string, action: () => Promise<unknown>) {
    setBusyId(id);
    try {
      await action();
      await load();
    } catch (e) {
      Alert.alert(
        'No se pudo completar la acción',
        e instanceof ApiError ? e.message : 'Intentá de nuevo.',
      );
    } finally {
      setBusyId(null);
    }
  }

  function confirmDelete(treatment: Treatment, medicationName: string) {
    Alert.alert(
      'Eliminar tratamiento',
      `¿Seguro que querés eliminar el tratamiento de ${medicationName}? Sus recordatorios se van a desactivar.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Eliminar',
          style: 'destructive',
          onPress: () => runAction(treatment.id, () => deleteTreatment(treatment.id)),
        },
      ],
    );
  }

  function confirmComplete(treatment: Treatment, medicationName: string) {
    Alert.alert(
      'Marcar como completado',
      patient.isOwn
        ? `¿Terminaste el tratamiento de ${medicationName}? Vas a dejar de recibir sus recordatorios. Si te equivocaste, lo podés reabrir.`
        : `¿Terminó el tratamiento de ${medicationName}? Se van a dejar de generar sus tomas. Si te equivocaste, lo podés reabrir.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Sí, lo terminé',
          onPress: () => runAction(treatment.id, () => completeTreatment(treatment.id)),
        },
      ],
    );
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
      <Text style={styles.title}>{patient.isOwn ? 'Mis tratamientos' : 'Tratamientos'}</Text>
      <View style={styles.newButton}>
        <AppButton title="Nuevo tratamiento" onPress={() => navigation.navigate('TreatmentForm')} />
      </View>
      <FlatList
        ref={listRef}
        data={treatments}
        persistentScrollbar
        keyExtractor={(treatment) => treatment.id}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <Text style={styles.message}>
            Todavía no {patient.isOwn ? 'tenés' : 'tiene'} tratamientos cargados.
          </Text>
        }
        renderItem={({ item }) => {
          const medication = medications[item.medicationId];
          const expanded = item.id === expandedId;
          return (
            <Pressable
              style={styles.card}
              onPress={() => toggle(item.id)}
              accessibilityRole="button"
              accessibilityState={{ expanded }}
              accessibilityHint="Muestra u oculta el detalle del tratamiento"
            >
              <View style={styles.cardHeader}>
                <Text style={styles.cardTitle}>{medication?.name}</Text>
                <Ionicons
                  name={expanded ? 'chevron-up' : 'chevron-down'}
                  size={24}
                  color={colors.primary}
                />
              </View>
              <Text style={styles.cardDetail}>
                {item.dosage}, {item.frequency}
              </Text>
              {hasEnded(item) ? (
                <Text style={styles.cardEnded}>Terminó el {formatDate(item.endDate ?? '')}</Text>
              ) : null}
              {needsSchedule(item) ? (
                <Pressable
                  style={styles.noSchedule}
                  onPress={() => navigation.navigate('Reminders', { treatmentId: item.id })}
                  accessibilityRole="button"
                  accessibilityHint="Abre los horarios del tratamiento"
                >
                  <Ionicons name="alert-circle-outline" size={22} color={colors.warning} />
                  <Text style={styles.noScheduleText}>
                    {patient.isOwn
                      ? 'Sin horarios: no vas a recibir recordatorios.'
                      : 'Sin horarios: no se van a generar sus tomas.'}{' '}
                    Tocá para configurarlos.
                  </Text>
                </Pressable>
              ) : null}
              <Text style={styles.toggleText}>{expanded ? 'Ocultar detalle' : 'Ver detalle'}</Text>
              {expanded ? (
                <View style={styles.details}>
                  <DetailRow label="Forma" value={medication?.administrationForm ?? '-'} />
                  <DetailRow label="Inicio" value={formatDate(item.startDate)} />
                  <DetailRow
                    label="Fin"
                    value={item.endDate ? formatDate(item.endDate) : 'Sin fecha de fin'}
                  />
                  <DetailRow label="Estado" value={treatmentState(item)} />
                  <View style={styles.actions}>
                    {!item.completed ? (
                      <>
                        <AppButton
                          title="Editar"
                          onPress={() =>
                            navigation.navigate('TreatmentForm', { treatmentId: item.id })
                          }
                        />
                        <AppButton
                          title="Horarios"
                          onPress={() => navigation.navigate('Reminders', { treatmentId: item.id })}
                        />
                        <AppButton
                          title="Marcar como completado"
                          loading={busyId === item.id}
                          onPress={() =>
                            confirmComplete(item, medication?.name ?? 'este medicamento')
                          }
                        />
                      </>
                    ) : (
                      <AppButton
                        title="Reabrir tratamiento"
                        loading={busyId === item.id}
                        onPress={() => runAction(item.id, () => reopenTreatment(item.id))}
                      />
                    )}

                    <AppButton
                      title="Eliminar"
                      variant="danger"
                      loading={busyId === item.id}
                      onPress={() => confirmDelete(item, medication?.name ?? 'este medicamento')}
                    />
                  </View>
                </View>
              ) : null}
            </Pressable>
          );
        }}
      />
    </Screen>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    justifyContent: 'center',
    gap: 16,
  },
  title: {
    marginTop: 16,
    marginBottom: 16,
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  list: {
    gap: 12,
    paddingBottom: 24,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
  card: {
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  cardDetail: {
    marginTop: 4,
    fontSize: 15,
    color: colors.textSecondary,
  },
  cardEnded: {
    marginTop: 4,
    fontSize: 15,
    fontWeight: '600',
    color: colors.warning,
  },
  noSchedule: {
    marginTop: 8,
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  noScheduleText: {
    flexShrink: 1,
    fontSize: 15,
    fontWeight: '600',
    color: colors.warning,
  },
  details: {
    marginTop: 12,
    paddingTop: 12,
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  rowLabel: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  rowValue: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  toggleText: {
    marginTop: 8,
    fontSize: 15,
    fontWeight: '600',
    color: colors.primary,
  },
  newButton: {
    marginBottom: 16,
  },
  actions: {
    marginTop: 8,
    gap: 8,
  },
});
