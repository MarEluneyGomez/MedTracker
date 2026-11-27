import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, StyleSheet, Text, View } from 'react-native';
import AppButton from '../components/AppButton';
import EmptyState from '../components/EmptyState';
import Screen from '../components/Screen';
import { useAuth } from '../context/AuthContext';
import { useFlashScrollIndicators } from '../hooks/useFlashScrollIndicators';
import { ApiError } from '../services/api';
import { getPatientCaregivers, removeCaregiverLink } from '../services/caregiverLinks';
import { colors } from '../theme/colors';
import type { UserSummary } from '../types/api';

export default function CaregiversScreen() {
  const { user } = useAuth();
  const [caregivers, setCaregivers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const listRef = useFlashScrollIndicators<FlatList<UserSummary>>(!loading);

  async function load() {
    if (!user) {
      return;
    }
    try {
      const linked = await getPatientCaregivers(user.id);
      setCaregivers([...linked].sort((a, b) => a.name.localeCompare(b.name)));
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudieron cargar tus cuidadores.');
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

  function confirmRemove(caregiver: UserSummary) {
    Alert.alert(
      'Quitar cuidador',
      `¿Quitar a ${caregiver.name} como cuidador? Va a dejar de ver y gestionar tus tratamientos y tus tomas.`,
      [
        { text: 'Volver', style: 'cancel' },
        { text: 'Quitar', style: 'destructive', onPress: () => handleRemove(caregiver) },
      ],
    );
  }

  async function handleRemove(caregiver: UserSummary) {
    if (!user) {
      return;
    }
    setBusyId(caregiver.id);
    try {
      await removeCaregiverLink(caregiver.id, user.id);
      setCaregivers((current) => current.filter((item) => item.id !== caregiver.id));
    } catch (e) {
      Alert.alert('No se pudo quitar', e instanceof ApiError ? e.message : 'Intentá de nuevo.');
    } finally {
      setBusyId(null);
    }
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
        data={caregivers}
        persistentScrollbar
        keyExtractor={(caregiver) => caregiver.id}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View style={styles.header}>
            <Text style={styles.title}>Mis cuidadores</Text>
            <Text style={styles.subtitle}>
              Pueden ver y gestionar tus tratamientos y tus tomas. No ven tus citas ni tu historial
              médico.
            </Text>
          </View>
        }
        ListEmptyComponent={
          <EmptyState icon="people-outline" message="Todavía no tenés cuidadores." />
        }
        renderItem={({ item }) => (
          <View style={styles.card}>
            <Text style={styles.name}>{item.name}</Text>
            <Text style={styles.email}>{item.email}</Text>
            <View style={styles.actions}>
              <AppButton
                title="Quitar cuidador"
                variant="danger"
                loading={busyId === item.id}
                onPress={() => confirmRemove(item)}
              />
            </View>
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
    flexGrow: 1,
    gap: 12,
    paddingBottom: 24,
  },
  header: {
    gap: 4,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: colors.text,
  },
  subtitle: {
    fontSize: 15,
    color: colors.textSecondary,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
  card: {
    padding: 16,
    gap: 2,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
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
  actions: {
    marginTop: 10,
  },
});
