import { createNativeStackNavigator } from '@react-navigation/native-stack';
import CaregiverPatientTabs from './CaregiverPatientTabs';
import CaregiverTabs from './CaregiverTabs';
import { backHeaderOptions } from './headerOptions';
import type { CaregiverStackParamList } from './types';

const Stack = createNativeStackNavigator<CaregiverStackParamList>();

export default function CaregiverStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="CaregiverHome" component={CaregiverTabs} />
      <Stack.Screen
        name="PatientDetail"
        component={CaregiverPatientTabs}
        options={({ route }) => ({ ...backHeaderOptions, title: route.params.patientName })}
      />
    </Stack.Navigator>
  );
}
