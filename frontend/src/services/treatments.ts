import type { Treatment } from '../types/api';
import { apiRequest } from './api';

export type TreatmentData = {
  medicationId: number;
  dosage: string;
  frequency: string;
  startDate: string; // formato "2026-10-20T08:00:00"
  endDate: string | null;
};

// El backend ya excluye los tratamientos eliminados lógicamente
export function getTreatmentsByUser(userId: string): Promise<Treatment[]> {
  return apiRequest<Treatment[]>('GET', `/treatments/user/${userId}`);
}

export function getTreatment(id: string): Promise<Treatment> {
  return apiRequest<Treatment>('GET', `/treatments/${id}`);
}

export function createTreatment(userId: string, data: TreatmentData): Promise<Treatment> {
  return apiRequest<Treatment>('POST', '/treatments/register', {
    user: { id: userId },
    medication: { id: data.medicationId },
    dosage: data.dosage.trim(),
    frequency: data.frequency.trim(),
    startDate: data.startDate,
    endDate: data.endDate,
  });
}

export function completeTreatment(id: string): Promise<Treatment> {
  return apiRequest<Treatment>('PUT', `/treatments/${id}/complete`);
}

// El medicamento no se puede cambiar: para otro medicamento se crea otro tratamiento
export type TreatmentChanges = Omit<TreatmentData, 'medicationId'>;

export function updateTreatment(id: string, changes: TreatmentChanges): Promise<Treatment> {
  return apiRequest<Treatment>('PUT', `/treatments/${id}`, {
    dosage: changes.dosage.trim(),
    frequency: changes.frequency.trim(),
    startDate: changes.startDate,
    endDate: changes.endDate,
  });
}

export function deleteTreatment(id: string): Promise<void> {
  return apiRequest<void>('DELETE', `/treatments/${id}`);
}
