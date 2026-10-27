# Listado de módulos

Módulos funcionales del sistema, derivados de los [requerimientos funcionales](../README.md#requerimientos-funcionales) y [casos de uso](../README.md#casos-de-uso) del README.

| Módulo | Descripción | Prioridad |
|---|---|---|
| Autenticación y gestión de usuarios | Registro e inicio de sesión de usuarios, con rol `PATIENT` o `CAREGIVER`. | Alta |
| Catálogo de medicamentos | Alta y búsqueda de medicamentos (nombre, forma de administración) disponibles para asociar a un tratamiento. | Alta |
| Gestión de tratamientos | ABM de tratamientos: asociación de un medicamento a un usuario con dosis, frecuencia y vigencia. | Alta |
| Recordatorios | Configuración de uno o más horarios de recordatorio por tratamiento. | Alta |
| Notificaciones push | Envío de recordatorios al dispositivo del usuario en el horario configurado, vía FCM. | Alta |
| Registro y seguimiento de tomas | Confirmación u omisión de cada toma programada, con marcado automático de tomas vencidas. | Alta |
| Historial y adherencia | Visualización del historial de tomas y cálculo de indicadores de adherencia al tratamiento. | Media |
| Vínculo caregiver-paciente | Gestión de la relación entre un tutor/responsable y los pacientes que administra. | Media |
| Cita médica | Agendamiento de una cita médica entre un paciente y un médico, con fecha, hora y motivo. | Media |
| Historial médico | Consulta del historial médico del paciente (diagnósticos y observaciones registradas). | Media |
