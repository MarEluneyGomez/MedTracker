# Listado de módulos

Módulos funcionales del sistema, derivados de los [requerimientos funcionales](../README.md#requerimientos-funcionales) y [casos de uso](../README.md#casos-de-uso) del README.

| Módulo | Descripción | Prioridad |
|---|---|---|
| Autenticación y gestión de usuarios | Registro e inicio de sesión de usuarios con roles `PATIENT`, `CAREGIVER` y `DOCTOR`. El auto-registro solo admite `PATIENT` o `CAREGIVER`; los médicos se dan de alta a mano (ver [Alta de médicos](../README.md#alta-de-médicos)). | Alta |
| Catálogo de medicamentos | Alta y búsqueda de medicamentos (nombre, forma de administración) disponibles para asociar a un tratamiento. | Alta |
| Gestión de tratamientos | ABM de tratamientos: asociación de un medicamento a un usuario con dosis, frecuencia y vigencia. | Alta |
| Recordatorios | Configuración de uno o más horarios de recordatorio por tratamiento. | Alta |
| Notificaciones y recordatorios | Aviso local de la app a la hora de cada toma y push de respaldo (FCM) desde el backend unos 15 minutos después (por defecto; configurable con `medtracker.push.delay-minutes`), solo si la toma sigue pendiente; el push está apagado por defecto (`medtracker.push.enabled=false`). | Alta |
| Registro y seguimiento de tomas | Confirmación u omisión de cada toma programada, con marcado automático de tomas vencidas. | Alta |
| Historial y adherencia | Visualización del historial de tomas y cálculo de indicadores de adherencia al tratamiento. | Media |
| Vínculo caregiver-paciente | Gestión de la relación entre un tutor/responsable y los pacientes que administra. | Media |
| Cita médica | Agendamiento de una cita médica entre un paciente y un médico, con fecha, hora y motivo; el paciente confirma las citas que agenda el médico. | Media |
| Historial médico | Consulta del historial médico del paciente (diagnósticos y observaciones registradas); el médico accede solo con una cita confirmada por el paciente. | Media |
