import { createNativeStackNavigator } from '@react-navigation/native-stack';
import CaregiversScreen from '../screens/CaregiversScreen';
import DeactivateAccountScreen from '../screens/DeactivateAccountScreen';
import MedicalHistoryScreen from '../screens/MedicalHistoryScreen';
import ProfileScreen from '../screens/ProfileScreen';
import { backHeaderOptions } from './headerOptions';
import type { ProfileStackParamList } from './types';

const Stack = createNativeStackNavigator<ProfileStackParamList>();

export default function ProfileStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="ProfileHome" component={ProfileScreen} />
      <Stack.Screen
        name="MedicalHistory"
        component={MedicalHistoryScreen}
        options={backHeaderOptions}
      />
      <Stack.Screen name="Caregivers" component={CaregiversScreen} options={backHeaderOptions} />
      <Stack.Screen
        name="DeactivateAccount"
        component={DeactivateAccountScreen}
        options={backHeaderOptions}
      />
    </Stack.Navigator>
  );
}
