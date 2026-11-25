import type { MedicalAppointment } from '../types/api';

export type AppointmentSection = {
  title: string;
  data: MedicalAppointment[];
};

export function isUpcoming(appointment: MedicalAppointment, now: Date): boolean {
  return appointment.status !== 'CANCELLED' && new Date(appointment.dateTime) >= now;
}

export function groupAppointments(
  appointments: MedicalAppointment[],
  now: Date,
): AppointmentSection[] {
  const byDate = (a: MedicalAppointment, b: MedicalAppointment) =>
    a.dateTime.localeCompare(b.dateTime);
  const upcoming = appointments.filter((item) => isUpcoming(item, now)).sort(byDate);
  const previous = appointments
    .filter((item) => !isUpcoming(item, now))
    .sort((a, b) => byDate(b, a));
  return [
    { title: 'Próximas', data: upcoming },
    { title: 'Anteriores y canceladas', data: previous },
  ].filter((section) => section.data.length > 0);
}
