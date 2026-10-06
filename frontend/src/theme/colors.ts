// Paleta de MedTracker. Las pantallas usan los nombres semánticos de
// `colors`, nunca los códigos sueltos, así un cambio de paleta se hace acá.
const palette = {
  lavender50: '#FBFAFF',
  lavender100: '#E6E1FF',
  lavender300: '#B8A7FF',
  slateBlue: '#6A5ACD',
  midnight: '#2A2455',
  white: '#FFFFFF',
  lavenderGray: '#5E5980',
  forest: '#26704F',
  forestTint: '#E3F4EC',
  brick: '#B3261E',
  brickTint: '#FBE7E5',
  amber: '#8A5A00',
  amberTint: '#FFF1D6',
} as const;

export const colors = {
  // Fondos
  background: palette.lavender50,
  surface: palette.white,
  surfaceTinted: palette.lavender100,

  // Marca y acciones
  primary: palette.slateBlue,
  onPrimary: palette.white,
  accent: palette.lavender300,
  onAccent: palette.midnight,

  // Texto (contraste sobre background: text 13.6:1, textSecondary 6.3:1,
  // textLink 5.1:1)
  text: palette.midnight,
  textSecondary: palette.lavenderGray,
  textLink: palette.slateBlue,

  // Estados (tono fuerte para texto e íconos, *Background para etiquetas).
  // Provisorios: pueden ajustarse más adelante. Siempre acompañarlos con
  // ícono o texto, no solo con el color.
  success: palette.forest,
  successBackground: palette.forestTint,
  error: palette.brick,
  errorBackground: palette.brickTint,
  warning: palette.amber,
  warningBackground: palette.amberTint,

  // Bordes y separadores
  border: palette.lavender100,
  borderStrong: palette.lavender300,
} as const;

export type ColorName = keyof typeof colors;
