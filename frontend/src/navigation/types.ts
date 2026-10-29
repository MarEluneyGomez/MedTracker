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
