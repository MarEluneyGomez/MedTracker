import { Pressable, StyleSheet, Text } from 'react-native';
import { colors } from '../theme/colors';

type ChipProps = {
  label: string;
  chosen: boolean;
  onPress: () => void;
};

export default function Chip({ label, chosen, onPress }: ChipProps) {
  return (
    <Pressable
      style={[styles.chip, chosen && styles.chipChosen]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: chosen }}
    >
      <Text style={[styles.chipText, chosen && styles.chipTextChosen]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: 12,
    backgroundColor: colors.surface,
  },
  chipChosen: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  chipText: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
  },
  chipTextChosen: {
    color: colors.onPrimary,
  },
});
