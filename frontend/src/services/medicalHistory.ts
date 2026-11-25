import type { MedicalHistory } from '../types/api';
import { apiRequest } from './api';

export function getMedicalHistory(patientId: string): Promise<MedicalHistory[]> {
  return apiRequest<MedicalHistory[]>('GET', `/medical-histories/patient/${patientId}`);
}
