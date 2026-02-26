const ISO_WITHOUT_TZ_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/;

function normalizeBackendDateInput(value: string): string {
  const trimmed = value.trim();
  // Backend currently returns LocalDateTime (no timezone). Treat as UTC to avoid
  // container timezone ambiguity (e.g. backend running in UTC, browser in UTC+8).
  if (ISO_WITHOUT_TZ_RE.test(trimmed)) {
    return `${trimmed}Z`;
  }
  return trimmed;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '';
  const parsed = new Date(normalizeBackendDateInput(value));
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
}
