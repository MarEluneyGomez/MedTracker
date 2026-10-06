export type UserRole = 'PATIENT' | 'CAREGIVER' | 'DOCTOR';

export type User = {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  fcmToken: string | null;
  createdAt: string;
  updatedAt: string;
};

export type Medication = {
  id: number;
  name: string;
  administrationForm: string;
  createdAt: string;
  updatedAt: string;
};

export type Treatment = {
  id: string;
  userId: string;
  medicationId: number;
  dosage: string;
  frequency: string;
  startDate: string;
  endDate: string | null;
  completed: boolean;
};

export type WeekDay = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';

export type Reminder = {
  id: string;
  treatmentId: string;
  time: string;
  daysOfWeek: WeekDay[] | null;
  message: string;
  active: boolean;
};

export type DoseStatus = 'PENDING' | 'CONFIRMED' | 'SKIPPED';

export type Dose = {
  id: string;
  reminderId: string;
  scheduledAt: string;
  confirmedAt: string | null;
  status: DoseStatus;
};

export type Notification = {
  id: string;
  userId: string;
  reminderId: string | null;
  message: string;
  read: boolean;
  sentAt: string;
};

export type CaregiverLink = {
  caregiverId: string;
  patientId: string;
  createdAt: string;
  updatedAt: string;
};

export type AppointmentStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';

export type MedicalAppointment = {
  id: string;
  patientId: string;
  doctorId: string;
  dateTime: string;
  reason: string;
  status: AppointmentStatus;
};

export type MedicalHistory = {
  id: string;
  patientId: string;
  diagnosis: string;
  notes: string | null;
  recordDate: string;
};
