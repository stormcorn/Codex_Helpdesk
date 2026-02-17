import { reactive, ref, type ComputedRef } from 'vue';
import { parseErrorMessage, requestJson } from './useApi';
import { useTextFeedback } from './useFeedbackState';
import type { AuditLogItem } from '../types';

type UseAuditLogsOptions = {
  isAdmin: ComputedRef<boolean>;
  authHeaders: () => HeadersInit;
};

export function useAuditLogs(options: UseAuditLogsOptions) {
  const auditLogs = ref<AuditLogItem[]>([]);
  const loadingAuditLogs = ref(false);
  const exportingAuditLogs = ref(false);
  const purgingAuditLogs = ref(false);
  const { feedback: auditLogsFeedback, clearFeedback: clearAuditLogsFeedback } = useTextFeedback();
  const { feedback: auditCleanupFeedback, clearFeedback: clearAuditCleanupFeedback } = useTextFeedback();
  const auditCleanupDays = ref(180);

  const auditFilters = reactive<{
    action: string;
    entityType: string;
    entityId: string;
    actorMemberId: string;
    from: string;
    to: string;
    limit: number;
  }>({
    action: '',
    entityType: '',
    entityId: '',
    actorMemberId: '',
    from: '',
    to: '',
    limit: 100
  });

  function formatJsonPreview(raw: string | null): string {
    if (!raw) return '-';
    try {
      const parsed = JSON.parse(raw);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return raw;
    }
  }

  function trimmedText(value: string, max = 140): string {
    const text = value.trim();
    if (!text) return '';
    return text.length <= max ? text : `${text.slice(0, max)}...`;
  }

  async function loadAuditLogs(): Promise<void> {
    if (!options.isAdmin.value) return;
    loadingAuditLogs.value = true;
    auditLogsFeedback.value = '';
    try {
      const params = new URLSearchParams();
      const action = trimmedText(auditFilters.action, 80).toUpperCase();
      const entityType = trimmedText(auditFilters.entityType, 80).toUpperCase();
      const entityId = trimmedText(auditFilters.entityId, 40);
      const actorMemberId = trimmedText(auditFilters.actorMemberId, 40);
      const from = trimmedText(auditFilters.from, 40);
      const to = trimmedText(auditFilters.to, 40);
      const safeLimit = Math.min(Math.max(Number(auditFilters.limit) || 100, 1), 500);

      if (action) params.set('action', action);
      if (entityType) params.set('entityType', entityType);
      if (entityId) params.set('entityId', entityId);
      if (actorMemberId) params.set('actorMemberId', actorMemberId);
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      params.set('limit', String(safeLimit));

      const query = params.toString();
      const url = query ? `/api/admin/audit-logs?${query}` : '/api/admin/audit-logs';
      auditLogs.value = await requestJson<AuditLogItem[]>(url, { headers: options.authHeaders() }, '讀取操作紀錄失敗');
    } catch (e) {
      auditLogsFeedback.value = e instanceof Error ? e.message : '讀取操作紀錄失敗';
    } finally {
      loadingAuditLogs.value = false;
    }
  }

  async function exportAuditLogsCsv(): Promise<void> {
    if (!options.isAdmin.value) return;
    exportingAuditLogs.value = true;
    auditLogsFeedback.value = '';
    try {
      const params = new URLSearchParams();
      const action = trimmedText(auditFilters.action, 80).toUpperCase();
      const entityType = trimmedText(auditFilters.entityType, 80).toUpperCase();
      const entityId = trimmedText(auditFilters.entityId, 40);
      const actorMemberId = trimmedText(auditFilters.actorMemberId, 40);
      const from = trimmedText(auditFilters.from, 40);
      const to = trimmedText(auditFilters.to, 40);
      const safeLimit = Math.min(Math.max(Number(auditFilters.limit) || 500, 1), 500);

      if (action) params.set('action', action);
      if (entityType) params.set('entityType', entityType);
      if (entityId) params.set('entityId', entityId);
      if (actorMemberId) params.set('actorMemberId', actorMemberId);
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      params.set('limit', String(safeLimit));

      const query = params.toString();
      const url = query ? `/api/admin/audit-logs/export.csv?${query}` : '/api/admin/audit-logs/export.csv';
      const response = await fetch(url, { headers: options.authHeaders() });
      if (!response.ok) {
        let parsed: unknown = null;
        try {
          parsed = await response.json();
        } catch {
          // ignore
        }
        throw new Error(parseErrorMessage('匯出 CSV 失敗', parsed));
      }

      const blob = await response.blob();
      const disposition = response.headers.get('Content-Disposition') ?? '';
      const matched = disposition.match(/filename="([^"]+)"/i);
      const filename = matched?.[1] ?? 'audit-logs.csv';
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = filename;
      anchor.click();
      URL.revokeObjectURL(objectUrl);
    } catch (e) {
      auditLogsFeedback.value = e instanceof Error ? e.message : '匯出 CSV 失敗';
    } finally {
      exportingAuditLogs.value = false;
    }
  }

  async function purgeAuditLogs(): Promise<void> {
    if (!options.isAdmin.value) return;
    purgingAuditLogs.value = true;
    auditCleanupFeedback.value = '';
    try {
      const safeDays = Math.min(Math.max(Number(auditCleanupDays.value) || 180, 1), 3650);
      const response = await requestJson<{
        configuredRetentionDays: number;
        requestedDays: number | null;
        cutoff: string;
        candidateCount: number;
        deletedCount: number;
      }>(
        `/api/admin/audit-logs/cleanup?days=${safeDays}`,
        { method: 'POST', headers: options.authHeaders() },
        '清理操作紀錄失敗'
      );
      auditCleanupFeedback.value = `清理完成：候選 ${response.candidateCount} 筆，刪除 ${response.deletedCount} 筆（cutoff: ${new Date(response.cutoff).toLocaleString()}）`;
      await loadAuditLogs();
    } catch (e) {
      auditCleanupFeedback.value = e instanceof Error ? e.message : '清理操作紀錄失敗';
    } finally {
      purgingAuditLogs.value = false;
    }
  }

  function clearAuditLogState(): void {
    auditLogs.value = [];
    clearAuditLogsFeedback();
    clearAuditCleanupFeedback();
  }

  return {
    auditLogs,
    loadingAuditLogs,
    exportingAuditLogs,
    purgingAuditLogs,
    auditLogsFeedback,
    auditCleanupFeedback,
    auditFilters,
    auditCleanupDays,
    formatJsonPreview,
    loadAuditLogs,
    exportAuditLogsCsv,
    purgeAuditLogs,
    clearAuditLogState
  };
}
