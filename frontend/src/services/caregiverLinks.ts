import type { CaregiverLink, UserSummary } from '../types/api';
import { apiRequest } from './api';
import { getUserSummary } from './users';

export async function getCaregiverPatients(caregiverId: string): Promise<UserSummary[]> {
  const links = await apiRequest<CaregiverLink[]>(
    'GET',
    `/caregiver-links/caregiver/${caregiverId}`,
  );
  return Promise.all(links.map((link) => getUserSummary(link.patientId)));
}

export async function getPatientCaregivers(patientId: string): Promise<UserSummary[]> {
  const links = await apiRequest<CaregiverLink[]>('GET', `/caregiver-links/patient/${patientId}`);
  return Promise.all(links.map((link) => getUserSummary(link.caregiverId)));
}

export function removeCaregiverLink(caregiverId: string, patientId: string): Promise<void> {
  return apiRequest<void>('DELETE', `/caregiver-links/${caregiverId}/${patientId}`);
}
