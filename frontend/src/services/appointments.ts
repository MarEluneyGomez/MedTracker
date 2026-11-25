import type { Doctor, MedicalAppointment } from '../types/api';
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
