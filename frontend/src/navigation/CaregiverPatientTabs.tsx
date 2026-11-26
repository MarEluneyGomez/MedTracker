import { Ionicons } from '@expo/vector-icons';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useRoute, type RouteProp } from '@react-navigation/native';
import { ActivePatientProvider } from '../context/ActivePatientContext';
import HistoryScreen from '../screens/HistoryScreen';
import TodayScreen from '../screens/TodayScreen';
import { colors } from '../theme/colors';
import TreatmentsStack from './TreatmentsStack';
import type { CaregiverStackParamList } from './types';

const Tab = createBottomTabNavigator();

export default function CaregiverPatientTabs() {
  const { params } = useRoute<RouteProp<CaregiverStackParamList, 'PatientDetail'>>();
  return (
    <ActivePatientProvider patient={{ id: params.patientId, name: params.patientName }}>
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
      </Tab.Navigator>
    </ActivePatientProvider>
  );
}
