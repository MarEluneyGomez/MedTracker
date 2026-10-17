import type { TextStyle } from 'react-native';
import { colors } from './colors';

// Tamaños pensados para usuarios mayores: las indicaciones y los errores
// tienen que leerse sin esfuerzo
export const fieldLabel: TextStyle = {
  marginBottom: 6,
  fontSize: 16,
  fontWeight: '600',
  color: colors.text,
};

export const fieldError: TextStyle = {
  fontSize: 15,
  color: colors.error,
};
