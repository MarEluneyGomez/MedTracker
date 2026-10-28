import { StyleSheet, Text } from 'react-native';
import { colors } from '../theme/colors';

export default function Logo() {
  return (
    <Text style={styles.logo}>
      <Text style={styles.med}>Med</Text>
      <Text style={styles.tracker}>Tracker</Text>
    </Text>
  );
}

const styles = StyleSheet.create({
  logo: {
    fontSize: 32,
    fontWeight: 'bold',
  },
  med: {
    color: colors.text,
  },
  tracker: {
    color: colors.accent,
  },
});
