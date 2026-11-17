import type { Dose } from '../types/api';
import { apiRequest } from './api';

export function getTodayDoses(userId: string): Promise<Dose[]> {
  return apiRequest<Dose[]>('GET', `/doses/user/${userId}`);
}

export function confirmDose(id: string): Promise<Dose> {
  return apiRequest<Dose>('PUT', `/doses/${id}/confirm`);
}

export function skipDose(id: string): Promise<Dose> {
  return apiRequest<Dose>('PUT', `/doses/${id}/skip`);
}

export function undoDose(id: string): Promise<Dose> {
  return apiRequest<Dose>('PUT', `/doses/${id}/undo`);
}
