import { createNativeStackNavigator } from '@react-navigation/native-stack';
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
    </Stack.Navigator>
  );
}
