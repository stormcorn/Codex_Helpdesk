export function parseErrorMessage(fallback: string, body: unknown): string {
  if (typeof body === 'object' && body !== null && 'message' in body) {
    const value = (body as { message?: unknown }).message;
    if (typeof value === 'string' && value) return value;
  }
  return fallback;
}

export async function requestJson<T>(url: string, init: RequestInit, fallback: string): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    let parsed: unknown = null;
    try {
      parsed = await response.json();
    } catch {
      // ignore
    }
    throw new Error(parseErrorMessage(fallback, parsed));
  }
  return (await response.json()) as T;
}
