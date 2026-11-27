import type { UserSummary } from '../types/api';
import { apiRequest } from './api';

export function getUserSummary(id: string): Promise<UserSummary> {
  return apiRequest<UserSummary>('GET', `/users/${id}`);
}
