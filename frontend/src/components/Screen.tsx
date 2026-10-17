import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaView, type Edge } from 'react-native-safe-area-context';
import { colors } from '../theme/colors';

type ScreenProps = {
  children: ReactNode;
  hasHeader?: boolean;
};

// Abajo nunca se agrega el área segura: la barra de pestañas ya la deja.
// Arriba tampoco cuando la pantalla tiene barra con flecha, que también la deja.
export default function Screen({ children, hasHeader = false }: ScreenProps) {
  const edges: Edge[] = hasHeader ? ['left', 'right'] : ['top', 'left', 'right'];
  return (
    <SafeAreaView style={styles.container} edges={edges}>
      {children}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    paddingHorizontal: 16,
  },
});
