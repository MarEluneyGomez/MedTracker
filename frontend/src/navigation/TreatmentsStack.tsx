import { createNativeStackNavigator } from '@react-navigation/native-stack';
import ReminderFormScreen from '../screens/ReminderFormScreen';
import RemindersScreen from '../screens/RemindersScreen';
import TreatmentFormScreen from '../screens/TreatmentFormScreen';
import TreatmentsScreen from '../screens/TreatmentsScreen';
import { backHeaderOptions } from './headerOptions';
import type { TreatmentsStackParamList } from './types';

const Stack = createNativeStackNavigator<TreatmentsStackParamList>();

export default function TreatmentsStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="TreatmentsList" component={TreatmentsScreen} />
      <Stack.Screen
        name="TreatmentForm"
        component={TreatmentFormScreen}
        options={backHeaderOptions}
      />
      <Stack.Screen name="Reminders" component={RemindersScreen} options={backHeaderOptions} />
      <Stack.Screen
        name="ReminderForm"
        component={ReminderFormScreen}
        options={backHeaderOptions}
      />
    </Stack.Navigator>
  );
}
