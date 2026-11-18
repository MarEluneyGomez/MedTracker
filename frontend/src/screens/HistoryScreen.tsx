import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState, type ComponentProps } from 'react';
import { ActivityIndicator, SectionList, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import ChipSelector from '../components/ChipSelector';
import DoseStatusLabel from '../components/DoseStatusLabel';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import { ApiError } from '../services/api';
import { getDoses, syncPendingDoseActions } from '../services/doses';
import { applyPendingDoseActions, loadPendingDoseActions } from '../services/pendingDoseActions';
import { getMedications } from '../services/medications';
import { getReminders } from '../services/reminders';
import { getTreatmentsByUser } from '../services/treatments';
import { colors } from '../theme/colors';
import type { Dose } from '../types/api';
import {
  PERIODS,
  adherenceByTreatment,
  adherenceLevel,
  adherenceOf,
  dueDosesInPeriod,
  groupByDay,
  type Adherence,
  type AdherenceLevel,
  type Period,
  type TreatmentAdherence,
} from '../utils/adherence';
import { formatClockTime, formatLongDate, fromBackendDate } from '../utils/dates';
import { buildDoseDetails, type DoseDetails } from '../utils/doseDetails';

type DaySection = {
  day: string;
  data: Dose[];
};

type LevelStyle = {
  label: string;
  icon: ComponentProps<typeof Ionicons>['name'];
  color: string;
};

const LEVELS: Record<AdherenceLevel, LevelStyle> = {
  good: { label: 'Buena', icon: 'checkmark-circle', color: colors.success },
  fair: { label: 'Regular', icon: 'alert-circle', color: colors.warning },
  low: { label: 'Baja', icon: 'close-circle', color: colors.error },
};

export default function HistoryScreen() {
  const { user } = useAuth();
  const [doses, setDoses] = useState<Dose[]>([]);
  const [details, setDetails] = useState<Record<string, DoseDetails>>({});
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());
  const [period, setPeriod] = useState<Period>('week');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useFlashScrollIndicators<SectionList<Dose, DaySection>>(!loading);

  async function load() {
    if (!user) {
      return;
    }
    try {
      await syncPendingDoseActions();
      const [allDoses, reminders, treatments, medications] = await Promise.all([
        getDoses(),
        getReminders(),
        getTreatmentsByUser(user.id),
        getMedications(),
      ]);
      const pending = await loadPendingDoseActions();
      setDoses(applyPendingDoseActions(allDoses, pending));
      setPendingIds(new Set(pending.map((action) => action.doseId)));
      setDetails(buildDoseDetails(reminders, treatments, medications));
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo cargar el historial.');
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

  function handlePeriod(label: string) {
    setPeriod(PERIODS.find((option) => option.label === label)?.period ?? period);
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

  const periodDoses = dueDosesInPeriod(doses, period);
  const overall = adherenceOf(periodDoses);
  const byTreatment = adherenceByTreatment(periodDoses, details);
  const sections: DaySection[] = groupByDay(periodDoses).map(({ day, doses: dayDoses }) => ({
    day,
    data: dayDoses,
  }));
  const periodLabel = PERIODS.find((option) => option.period === period)?.label ?? null;

  return (
    <Screen>
      <SectionList
        ref={listRef}
        sections={sections}
        persistentScrollbar
        stickySectionHeadersEnabled={false}
        keyExtractor={(dose) => dose.id}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View style={styles.header}>
            <Text style={styles.title}>Historial</Text>
            <ChipSelector
              label="Período"
              options={PERIODS.map(({ label }) => label)}
              value={periodLabel}
              onChange={handlePeriod}
            />
            {overall.percent !== null ? (
              <>
                <AdherenceSummary adherence={overall} />
                <Text style={styles.sectionTitle}>Por tratamiento</Text>
                {byTreatment.map((item) => (
                  <TreatmentRow key={item.treatmentId} item={item} />
                ))}
                <Text style={styles.sectionTitle}>Detalle por día</Text>
              </>
            ) : null}
          </View>
        }
        ListEmptyComponent={
          <Text style={styles.message}>Todavía no hay tomas en este período.</Text>
        }
        renderSectionHeader={({ section }) => (
          <Text style={styles.dayTitle}>{formatLongDate(fromBackendDate(section.day))}</Text>
        )}
        renderItem={({ item }) => (
          <View style={styles.doseRow}>
            <Text style={styles.doseTime}>{formatClockTime(item.scheduledAt)}</Text>
            <View style={styles.doseInfo}>
              <Text style={styles.doseName}>
                {details[item.reminderId]?.medicationName ?? 'Medicamento'}
              </Text>
              <DoseStatusLabel dose={item} isPendingSync={pendingIds.has(item.id)} />
            </View>
          </View>
        )}
      />
    </Screen>
  );
}

function AdherenceSummary({ adherence }: { adherence: Adherence }) {
  const percent = adherence.percent ?? 0;
  const level = LEVELS[adherenceLevel(percent)];
  return (
    <View style={styles.summary}>
      <View style={styles.summaryRow}>
        <Text style={styles.percent}>{percent} %</Text>
        <View style={styles.level}>
          <Ionicons name={level.icon} size={24} color={level.color} />
          <Text style={[styles.levelText, { color: level.color }]}>{level.label}</Text>
        </View>
      </View>
      <Text style={styles.summaryText}>
        Tomaste {adherence.confirmed} de {adherence.total} tomas
      </Text>
      <View style={styles.progressTrack}>
        <View
          style={[styles.progressFill, { width: `${percent}%`, backgroundColor: level.color }]}
        />
      </View>
    </View>
  );
}

function TreatmentRow({ item }: { item: TreatmentAdherence }) {
  const percent = item.adherence.percent ?? 0;
  const level = LEVELS[adherenceLevel(percent)];
  return (
    <View style={styles.treatmentRow}>
      <View style={styles.treatmentInfo}>
        <Text style={styles.treatmentName}>{item.medicationName}</Text>
        <Text style={styles.treatmentDosage}>{item.dosage}</Text>
      </View>
      <View style={styles.treatmentStats}>
        <Text style={[styles.treatmentPercent, { color: level.color }]}>{percent} %</Text>
        <Text style={styles.treatmentCount}>
          {item.adherence.confirmed} de {item.adherence.total}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    justifyContent: 'center',
    gap: 16,
  },
  list: {
    gap: 8,
    paddingBottom: 24,
  },
  header: {
    marginTop: 16,
    gap: 16,
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
  summary: {
    gap: 8,
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  summaryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  percent: {
    fontSize: 40,
    fontWeight: 'bold',
    color: colors.text,
  },
  level: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  levelText: {
    fontSize: 18,
    fontWeight: '600',
  },
  summaryText: {
    fontSize: 16,
    color: colors.textSecondary,
  },
  progressTrack: {
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.surfaceTinted,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
  },
  treatmentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  treatmentInfo: {
    flex: 1,
  },
  treatmentName: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  treatmentDosage: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  treatmentStats: {
    alignItems: 'flex-end',
  },
  treatmentPercent: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  treatmentCount: {
    fontSize: 14,
    color: colors.textSecondary,
  },
  dayTitle: {
    marginTop: 12,
    fontSize: 17,
    fontWeight: '600',
    color: colors.text,
  },
  doseRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    padding: 14,
    borderRadius: 12,
    backgroundColor: colors.surface,
  },
  doseTime: {
    fontSize: 22,
    fontWeight: 'bold',
    fontVariant: ['tabular-nums'],
    color: colors.text,
  },
  doseInfo: {
    flex: 1,
    gap: 2,
  },
  doseName: {
    fontSize: 17,
    fontWeight: '600',
    color: colors.text,
  },
});
