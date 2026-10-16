import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/colors';
import type { UserRole } from '../types/api';

const OPTIONS: { value: UserRole; label: string }[] = [
  { value: 'PATIENT', label: 'Paciente' },
  { value: 'CAREGIVER', label: 'Tutor' },
  { value: 'DOCTOR', label: 'Médico' },
];

type RoleSelectorProps = {
  value: UserRole;
  onChange: (role: UserRole) => void;
};

export default function RoleSelector({ value, onChange }: RoleSelectorProps) {
  return (
    <View>
      <Text style={styles.label}>Rol</Text>
      <View style={styles.options}>
        {OPTIONS.map((option) => {
          const selected = option.value === value;
          return (
            <Pressable
              key={option.value}
              onPress={() => onChange(option.value)}
              style={[styles.option, selected && styles.optionSelected]}
            >
              <Text style={[styles.optionText, selected && styles.optionTextSelected]}>
                {option.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  label: {
    marginBottom: 6,
    fontSize: 14,
    fontWeight: '600',
    color: colors.text,
  },
  option: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: 12,
    backgroundColor: colors.surface,
  },
  options: {
    flexDirection: 'row',
    gap: 8,
  },
  optionSelected: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  optionText: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
  },
  optionTextSelected: {
    color: colors.onPrimary,
  },
});
