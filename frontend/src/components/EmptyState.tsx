import { Ionicons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';

type EmptyStateProps = {
  icon: ComponentProps<typeof Ionicons>['name'];
  message: string;
};

export default function EmptyState({ icon, message }: EmptyStateProps) {
  return (
    <View style={styles.container}>
      <Ionicons name={icon} size={48} color={colors.textSecondary} />
      <Text style={styles.message}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 32,
    paddingHorizontal: 16,
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: colors.textSecondary,
  },
});
