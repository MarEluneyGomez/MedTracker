import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';
import { useOfflineDataSavedAt } from '../hooks/useOfflineDataSavedAt';
import { colors } from '../theme/colors';
import { formatDayAndTime } from '../utils/dates';

export default function OfflineBanner() {
  const savedAt = useOfflineDataSavedAt();
  if (!savedAt) {
    return null;
  }
  return (
    <View style={styles.banner} accessibilityRole="alert">
      <Ionicons name="cloud-offline-outline" size={24} color={colors.warning} />
      <Text style={styles.text}>
        Sin conexión. Estás viendo los datos guardados el {formatDayAndTime(new Date(savedAt))}.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 8,
    padding: 12,
    borderRadius: 12,
    backgroundColor: colors.warningBackground,
  },
  text: {
    flex: 1,
    fontSize: 15,
    fontWeight: '600',
    color: colors.warning,
  },
});
