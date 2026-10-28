import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import { fieldLabel } from '../theme/typography';

type ChipSelectorProps = {
  label: string;
  options: string[];
  value: string | null;
  onChange: (option: string) => void;
};

export default function ChipSelector({ label, options, value, onChange }: ChipSelectorProps) {
  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.options}>
        {options.map((option) => {
          const chosen = option === value;
          return (
            <Pressable
              key={option}
              style={[styles.chip, chosen && styles.chipChosen]}
              onPress={() => onChange(option)}
              accessibilityRole="button"
              accessibilityState={{ selected: chosen }}
            >
              <Text style={[styles.chipText, chosen && styles.chipTextChosen]}>{option}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  label: fieldLabel,
  options: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
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
