import { Ionicons } from '@expo/vector-icons';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import HistoryScreen from '../screens/HistoryScreen';
import AppointmentsStack from './AppointmentsStack';
import ProfileStack from './ProfileStack';
import TodayScreen from '../screens/TodayScreen';
import { colors } from '../theme/colors';
import TreatmentsStack from './TreatmentsStack';
import { useDoseNotificationsSync } from '../hooks/useDoseNotificationsSync';
import { useOpenTodayOnNotificationTap } from '../hooks/useOpenTodayOnNotificationTap';
import { usePushTokenRegistration } from '../hooks/usePushTokenRegistration';

const Tab = createBottomTabNavigator();

export default function PatientTabs() {
  useDoseNotificationsSync();
  useOpenTodayOnNotificationTap();
  usePushTokenRegistration();
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textSecondary,
      }}
    >
      <Tab.Screen
        name="Today"
        component={TodayScreen}
        options={{
          title: 'Hoy',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="today-outline" color={color} size={size} />
          ),
        }}
      />

      <Tab.Screen
        name="Treatments"
        component={TreatmentsStack}
        options={{
          title: 'Tratamientos',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="medkit-outline" color={color} size={size} />
          ),
        }}
      />

      <Tab.Screen
        name="History"
        component={HistoryScreen}
        options={{
          title: 'Historial',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="stats-chart-outline" color={color} size={size} />
          ),
        }}
      />

      <Tab.Screen
        name="Appointments"
        component={AppointmentsStack}
        options={{
          title: 'Citas',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="calendar-outline" color={color} size={size} />
          ),
        }}
      />

      <Tab.Screen
        name="Profile"
        component={ProfileStack}
        options={{
          title: 'Perfil',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="person-outline" color={color} size={size} />
          ),
        }}
      />
    </Tab.Navigator>
  );
}
