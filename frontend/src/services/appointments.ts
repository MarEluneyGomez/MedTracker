import type { Doctor, MedicalAppointment, UserSummary } from '../types/api';
import { apiRequest } from './api';

export function getAppointments(): Promise<MedicalAppointment[]> {
  return apiRequest<MedicalAppointment[]>('GET', '/medical-appointments');
}

export function getDoctors(): Promise<Doctor[]> {
  return apiRequest<Doctor[]>('GET', '/users?role=DOCTOR');
}

export function confirmAppointment(id: string): Promise<MedicalAppointment> {
  return apiRequest<MedicalAppointment>(
    'PUT',
    `/medical-appointments/${id}/status?status=CONFIRMED`,
  );
}

export function cancelAppointment(id: string): Promise<MedicalAppointment> {
  return apiRequest<MedicalAppointment>(
    'PUT',
    `/medical-appointments/${id}/status?status=CANCELLED`,
  );
}

export function getPatient(id: string): Promise<UserSummary> {
  return apiRequest<UserSummary>('GET', `/users/${id}`);
}

export async function getConfirmedPatients(
  appointments: MedicalAppointment[],
): Promise<UserSummary[]> {
  const ids = new Set(
    appointments.filter((item) => item.status === 'CONFIRMED').map((item) => item.patientId),
  );
  const results = await Promise.allSettled([...ids].map(getPatient));
  return results.filter((result) => result.status === 'fulfilled').map((result) => result.value);
}

export type NewAppointment = {
  patientId: string;
  doctorId: string;
  dateTime: string;
  reason: string;
};

export function createAppointment(data: NewAppointment): Promise<MedicalAppointment> {
  return apiRequest<MedicalAppointment>('POST', '/medical-appointments/register', {
    patient: { id: data.patientId },
    doctor: { id: data.doctorId },
    dateTime: data.dateTime,
    reason: data.reason.trim(),
  });
}
