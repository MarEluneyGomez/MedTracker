import { createNativeStackNavigator } from '@react-navigation/native-stack';
import DoctorPatientsScreen from '../screens/DoctorPatientsScreen';
import MedicalHistoryFormScreen from '../screens/MedicalHistoryFormScreen';
import MedicalHistoryScreen from '../screens/MedicalHistoryScreen';
import { backHeaderOptions } from './headerOptions';
import type { DoctorPatientsStackParamList } from './types';

const Stack = createNativeStackNavigator<DoctorPatientsStackParamList>();

export default function DoctorPatientsStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="PatientsList" component={DoctorPatientsScreen} />
      <Stack.Screen
        name="PatientHistory"
        component={MedicalHistoryScreen}
        options={backHeaderOptions}
      />
      <Stack.Screen
        name="MedicalHistoryForm"
        component={MedicalHistoryFormScreen}
        options={backHeaderOptions}
      />
    </Stack.Navigator>
  );
}
