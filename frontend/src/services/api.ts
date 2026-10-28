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

const REQUEST_TIMEOUT_MS = 15000;
const NO_CONTENT = 204;
const UNAUTHORIZED = 401;

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

let authToken: string | null = null;
let onSessionExpired: (() => void) | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler;
}

export async function apiRequest<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
  const token = authToken;
  const headers: Record<string, string> = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const options: RequestInit = { method, headers };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, { ...options, signal: controller.signal });
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor. Revisá tu conexión.');
  } finally {
    clearTimeout(timeout);
  }

  // Con token, un 401 significa que la sesión venció (sin token es un login
  // con credenciales incorrectas). Solo se avisa si el token sigue siendo el
  // actual, para no avisar una vez por cada pedido que estaba en curso.
  if (response.status === UNAUTHORIZED && token) {
    if (token === authToken) {
      onSessionExpired?.();
    }
    throw new ApiError(UNAUTHORIZED, ERROR_MESSAGES[UNAUTHORIZED]);
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response));
  }
  if (response.status === NO_CONTENT) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// El backend solo manda mensajes pensados para el usuario; si no manda
// ninguno, se usa el de la tabla según el código
async function readErrorMessage(response: Response): Promise<string> {
  const fallback =
    ERROR_MESSAGES[response.status] ?? 'Ocurrió un error inesperado. Intentá de nuevo.';
  try {
    const data = await response.json();
    const message = typeof data.message === 'string' ? data.message.trim() : '';
    return message || fallback;
  } catch {
    return fallback;
  }
}
