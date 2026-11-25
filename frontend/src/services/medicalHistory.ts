import type { MedicalHistory } from '../types/api';
import { apiRequest } from './api';

export type NewMedicalHistory = {
  patientId: string;
  diagnosis: string;
  notes: string;
  recordDate: string;
};

export function getMedicalHistory(patientId: string): Promise<MedicalHistory[]> {
  return apiRequest<MedicalHistory[]>('GET', `/medical-histories/patient/${patientId}`);
}

export function createMedicalHistory(data: NewMedicalHistory): Promise<MedicalHistory> {
  return apiRequest<MedicalHistory>('POST', '/medical-histories/register', {
    patient: { id: data.patientId },
    diagnosis: data.diagnosis.trim(),
    notes: data.notes.trim() || null,
    recordDate: data.recordDate,
  });
}
