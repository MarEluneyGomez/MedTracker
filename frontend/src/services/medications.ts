import type { Medication } from '../types/api';
import { apiRequest } from './api';
import { capitalizeFirst } from '../utils/text';

export type NewMedication = {
  name: string;
  administrationForm: string;
};

export function getMedications(): Promise<Medication[]> {
  return apiRequest<Medication[]>('GET', '/medications');
}

export function createMedication(data: NewMedication): Promise<Medication> {
  return apiRequest<Medication>('POST', '/medications/register', {
    name: capitalizeFirst(data.name),
    administrationForm: data.administrationForm.trim(),
  });
}

export function getMedication(id: number): Promise<Medication> {
  return apiRequest<Medication>('GET', `/medications/${id}`);
}
