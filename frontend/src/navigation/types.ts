export type RootStackParamList = {
  Welcome: undefined;
  Login: undefined;
  Register: undefined;
  Main: undefined;
};

export type TreatmentsStackParamList = {
  TreatmentsList: undefined;
  TreatmentForm: { treatmentId?: string } | undefined;
  Reminders: { treatmentId: string };
  ReminderForm: { treatmentId: string; reminderId?: string };
};

export type ProfileStackParamList = {
  ProfileHome: undefined;
  MedicalHistory: undefined;
  DeactivateAccount: undefined;
  Caregivers: undefined;
};

export type DoctorPatientsStackParamList = {
  PatientsList: undefined;
  PatientHistory: { patientId: string; patientName: string };
  MedicalHistoryForm: { patientId: string; patientName: string };
};

export type CaregiverStackParamList = {
  CaregiverHome: undefined;
  PatientDetail: { patientId: string; patientName: string };
};

export type AppointmentsStackParamList = {
  AppointmentsList: undefined;
  AppointmentForm: undefined;
};
