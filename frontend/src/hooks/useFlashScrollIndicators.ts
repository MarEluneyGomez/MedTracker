import { useEffect, useRef } from 'react';
import { Platform } from 'react-native';

type Scrollable = { flashScrollIndicators: () => void };

const FLASH_DELAY_MS = 500;

// iOS no permite dejar la barra de desplazamiento siempre visible (en Android
// lo hace persistentScrollbar): se la muestra un momento al abrir la pantalla
// para avisar que hay más contenido abajo
export function useFlashScrollIndicators<T extends Scrollable>(ready = true) {
  const ref = useRef<T>(null);

  useEffect(() => {
    if (Platform.OS !== 'ios' || !ready) {
      return;
    }
    const timer = setTimeout(() => ref.current?.flashScrollIndicators(), FLASH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [ready]);

  return ref;
}
