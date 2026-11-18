import { LocaleConfig } from 'react-native-calendars';
import { DAY_NAMES, MONTH_NAMES } from './dates';

LocaleConfig.locales.es = {
  monthNames: MONTH_NAMES,
  monthNamesShort: [
    'Ene',
    'Feb',
    'Mar',
    'Abr',
    'May',
    'Jun',
    'Jul',
    'Ago',
    'Sep',
    'Oct',
    'Nov',
    'Dic',
  ],
  dayNames: DAY_NAMES,
  dayNamesShort: ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'],
  today: 'Hoy',
};

LocaleConfig.defaultLocale = 'es';
