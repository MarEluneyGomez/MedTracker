import { StyleSheet, Text, View } from 'react-native';
import { fieldLabel } from '../theme/typography';
import Chip from './Chip';

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
        {options.map((option) => (
          <Chip
            key={option}
            label={option}
            chosen={option === value}
            onPress={() => onChange(option)}
          />
        ))}
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
});
