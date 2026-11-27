import { createNativeStackNavigator } from '@react-navigation/native-stack';
import AppointmentFormScreen from '../screens/AppointmentFormScreen';
import AppointmentsScreen from '../screens/AppointmentsScreen';
import { backHeaderOptions } from './headerOptions';
import type { AppointmentsStackParamList } from './types';

const Stack = createNativeStackNavigator<AppointmentsStackParamList>();

export default function AppointmentsStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="AppointmentsList" component={AppointmentsScreen} />
      <Stack.Screen
        name="AppointmentForm"
        component={AppointmentFormScreen}
        options={backHeaderOptions}
      />
    </Stack.Navigator>
  );
}
