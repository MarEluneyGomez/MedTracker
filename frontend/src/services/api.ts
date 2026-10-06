const ERROR_MESSAGES: Record<number, string> = {
  400: 'Los datos enviados no son válidos.',
  401: 'Tu sesión expiró. Volvé a iniciar sesión.',
  403: 'No tenés permiso para hacer esta acción.',
  404: 'No se encontró lo que buscabas.',
  408: 'La solicitud tardó demasiado. Intentá de nuevo.',
  409: 'Ya existe un registro con esos datos.',
  429: 'Hiciste demasiados intentos. Esperá un momento.',
  500: 'Ocurrió un error en el servidor. Intentá más tarde.',
  502: 'El servidor no está disponible. Intentá más tarde.',
  503: 'El servicio no está disponible en este momento. Intentá más tarde.',
  504: 'El servidor tardó demasiado en responder. Intentá más tarde.',
};

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://10.0.2.2:8080';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export async function apiRequest<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
  const options: RequestInit = { method };

  if (body !== undefined) {
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify(body);
  }

  let response: Response;

  try {
    response = await fetch(`${API_URL}${path}`, options);
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor. Revisá tu conexión.');
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      ERROR_MESSAGES[response.status] ?? 'Ocurrió un error inesperado, intenta de nuevo.',
    );
  }
  return (await response.json()) as T;
}
