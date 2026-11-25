import type { CaregiverLink, UserSummary } from '../types/api';
import { apiRequest } from './api';
import { getPatient } from './appointments';

export async function getCaregiverPatients(caregiverId: string): Promise<UserSummary[]> {
  const links = await apiRequest<CaregiverLink[]>(
    'GET',
    `/caregiver-links/caregiver/${caregiverId}`,
  );
  return Promise.all(links.map((link) => getPatient(link.patientId)));
}
