import type { Medication, Reminder, Treatment } from '../types/api';

export type DoseDetails = {
  treatmentId: string;
  medicationName: string;
  dosage: string;
};

// La toma solo trae su recordatorio: el medicamento y la dosis salen del tratamiento
export function buildDoseDetails(
  reminders: Reminder[],
  treatments: Treatment[],
  medications: Medication[],
): Record<string, DoseDetails> {
  const treatmentsById = new Map(treatments.map((treatment) => [treatment.id, treatment]));
  const medicationsById = new Map(medications.map((medication) => [medication.id, medication]));
  const details: Record<string, DoseDetails> = {};
  for (const reminder of reminders) {
    const treatment = treatmentsById.get(reminder.treatmentId);
    if (treatment) {
      details[reminder.id] = {
        treatmentId: treatment.id,
        medicationName: medicationsById.get(treatment.medicationId)?.name ?? 'Medicamento',
        dosage: treatment.dosage,
      };
    }
  }
  return details;
}
