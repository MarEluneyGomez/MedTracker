import { readFromCache, saveToCache } from './cache';
import { markOnline, markShowingSavedData } from './connection';

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
export const UNAUTHORIZED = 401;
const NO_CONNECTION = 0;

export function isConnectionError(error: unknown): boolean {
  return error instanceof ApiError && error.status === NO_CONNECTION;
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

type RequestOptions = {
  // false en login, registro, renovación y logout: no llevan token ni intentan renovar la sesión
  authenticated?: boolean;
};

let authToken: string | null = null;
let renewSession: (() => Promise<string | null>) | null = null;
let pendingRenewal: Promise<string | null> | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

export function setSessionRenewer(renewer: (() => Promise<string | null>) | null) {
  renewSession = renewer;
}

export async function apiRequest<T>(
  method: HttpMethod,
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const isCacheable = method === 'GET' && (options.authenticated ?? true);
  try {
    const data = await requestFromServer<T>(method, path, body, options);
    if (isCacheable) {
      // Si no se puede guardar la copia, la app sigue igual: solo no habrá datos sin conexión
      saveToCache(path, data).catch(() => undefined);
    }
    return data;
  } catch (e) {
    const cached = isCacheable && isConnectionError(e) ? await readFromCache<T>(path) : null;
    if (!cached) {
      throw e;
    }
    markShowingSavedData(cached.savedAt);
    return cached.data;
  }
}

async function requestFromServer<T>(
  method: HttpMethod,
  path: string,
  body: unknown,
  { authenticated = true }: RequestOptions,
): Promise<T> {
  const token = authenticated ? authToken : null;
  let response = await send(method, path, body, token);

  if (response.status === UNAUTHORIZED && token) {
    const renewedToken = await renewToken(token);
    if (!renewedToken) {
      throw new ApiError(UNAUTHORIZED, ERROR_MESSAGES[UNAUTHORIZED]);
    }
    response = await send(method, path, body, renewedToken);
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response));
  }
  if (response.status === NO_CONTENT) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function send(
  method: HttpMethod,
  path: string,
  body: unknown,
  token: string | null,
): Promise<Response> {
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
  try {
    const response = await fetch(`${API_URL}${path}`, { ...options, signal: controller.signal });
    markOnline();
    return response;
  } catch {
    throw new ApiError(NO_CONNECTION, 'No se pudo conectar con el servidor. Revisá tu conexión.');
  } finally {
    clearTimeout(timeout);
  }
}

// Si otro pedido ya renovó la sesión se usa ese token. Si no, se renueva una
// sola vez aunque fallen varios pedidos juntos: el backend toma un refresh
// token usado dos veces como robado y cierra todas las sesiones.
async function renewToken(expiredToken: string): Promise<string | null> {
  if (authToken !== expiredToken) {
    return authToken;
  }
  if (!renewSession) {
    return null;
  }
  pendingRenewal ??= renewSession().finally(() => {
    pendingRenewal = null;
  });
  return pendingRenewal;
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
