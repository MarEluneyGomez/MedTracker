import type { User } from '../../types/api';

// Todos los usuarios de prueba usan esta contraseña
export const MOCK_PASSWORD = '1234';

export const mockUsers: User[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Ana Pérez',
    email: 'paciente@demo.com',
    role: 'PATIENT',
    fcmToken: null,
    createdAt: '2026-10-01T09:00:00',
    updatedAt: '2026-10-01T09:00:00',
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Carlos Gómez',
    email: 'tutor@demo.com',
    role: 'CAREGIVER',
    fcmToken: null,
    createdAt: '2026-10-01T09:00:00',
    updatedAt: '2026-10-01T09:00:00',
  },
  {
    id: '99999999-9999-9999-9999-999999999999',
    name: 'Dra. Laura Ríos',
    email: 'medico@demo.com',
    role: 'DOCTOR',
    fcmToken: null,
    createdAt: '2026-10-01T09:00:00',
    updatedAt: '2026-10-01T09:00:00',
  },
];
