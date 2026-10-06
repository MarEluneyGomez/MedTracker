import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ActivityIndicator, View } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { colors } from '../theme/colors';
import WelcomeScreen from '../screens/WelcomeScreen';
import type { UserRole } from '../types/api';
import PatientTabs from './PatientTabs';
import CaregiverTabs from './CaregiverTabs';
import DoctorTabs from './DoctorTabs';

const Stack = createNativeStackNavigator();

function tabsForRole(role: UserRole) {
  switch (role) {
    case 'PATIENT':
      return PatientTabs;
    case 'CAREGIVER':
      return CaregiverTabs;
    case 'DOCTOR':
      return DoctorTabs;
  }
}

export default function RootNavigator() {
  const { user, isRestoring } = useAuth();

  if (isRestoring) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', backgroundColor: colors.background }}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {user ? (
          <Stack.Screen name="Main" component={tabsForRole(user.role)} />
        ) : (
          <Stack.Screen name="Welcome" component={WelcomeScreen} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
