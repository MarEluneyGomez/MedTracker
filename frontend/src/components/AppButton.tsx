import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native';
import { colors } from '../theme/colors';

type AppButtonProps = {
  title: string;
  onPress: () => void;
  loading?: boolean;
  variant?: 'primary' | 'danger';
};

export default function AppButton({
  title,
  onPress,
  loading,
  variant = 'primary',
}: AppButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={loading}
      style={({ pressed }) => [
        styles.button,
        variant === 'danger' && styles.danger,
        pressed && styles.pressed,
        loading && styles.disabled,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={colors.onPrimary} />
      ) : (
        <Text style={styles.text}>{title}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    backgroundColor: colors.primary,
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 12,
    alignItems: 'center',
  },
  danger: {
    backgroundColor: colors.error,
  },
  text: {
    color: colors.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
  pressed: {
    opacity: 0.8,
  },
  disabled: {
    opacity: 0.6,
  },
});
