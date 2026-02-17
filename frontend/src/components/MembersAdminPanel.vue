<script setup lang="ts">
import { computed } from 'vue';
import type { AdminGroup, AuditLogItem, HelpdeskCategory, Member } from '../types';

type AuditFilters = {
  action: string;
  entityType: string;
  entityId: string;
  actorMemberId: string;
  from: string;
  to: string;
  limit: number;
};

const props = defineProps<{
  membersFeedback: string;
  loadingMembers: boolean;
  members: Member[];
  groupsFeedback: string;
  createGroupName: string;
  groupAssignForm: { groupId: number | null; memberId: number | null };
  loadingGroups: boolean;
  adminGroups: AdminGroup[];
  categoryFeedback: string;
  createCategoryName: string;
  adminHelpdeskCategories: HelpdeskCategory[];
  exportingAuditLogs: boolean;
  auditLogsFeedback: string;
  auditCleanupFeedback: string;
  auditCleanupDays: number;
  purgingAuditLogs: boolean;
  auditFilters: AuditFilters;
  loadingAuditLogs: boolean;
  auditLogs: AuditLogItem[];
  formatJsonPreview: (value: string | null) => string;
}>();

const emit = defineEmits<{
  'update:createGroupName': [value: string];
  'update:createCategoryName': [value: string];
  'update:auditCleanupDays': [value: number];
  updateMemberRole: [member: Member, role: 'USER' | 'IT'];
  deleteMember: [member: Member];
  createAdminGroup: [];
  addMemberToGroup: [];
  setGroupSupervisor: [groupId: number, memberId: number];
  removeMemberFromGroup: [groupId: number, memberId: number];
  createHelpdeskCategory: [];
  loadAuditLogs: [];
  exportAuditLogsCsv: [];
  purgeAuditLogs: [];
}>();

const createGroupNameModel = computed({
  get: () => props.createGroupName,
  set: (value: string) => emit('update:createGroupName', value)
});

const createCategoryNameModel = computed({
  get: () => props.createCategoryName,
  set: (value: string) => emit('update:createCategoryName', value)
});

const auditCleanupDaysModel = computed({
  get: () => props.auditCleanupDays,
  set: (value: number) => emit('update:auditCleanupDays', value)
});
</script>

<template>
  <section class="panel">
    <h2>成員管理（Admin）</h2>
    <p class="subtitle">可指派 USER / IT。不可將任一帳號設為 ADMIN，也不可刪除 ADMIN。</p>
    <p v-if="props.membersFeedback" class="feedback error">{{ props.membersFeedback }}</p>
    <p v-if="props.loadingMembers">讀取成員中...</p>
    <table v-else class="member-table">
      <thead>
        <tr>
          <th>工號</th><th>姓名</th><th>Email</th><th>角色</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in props.members" :key="m.id">
          <td>{{ m.employeeId }}</td>
          <td>{{ m.name }}</td>
          <td>{{ m.email }}</td>
          <td>{{ m.role }}</td>
          <td>
            <template v-if="m.role !== 'ADMIN'">
              <div class="row">
                <button @click="emit('updateMemberRole', m, 'USER')">設為 USER</button>
                <button @click="emit('updateMemberRole', m, 'IT')">設為 IT</button>
                <button class="danger" @click="emit('deleteMember', m)">刪除</button>
              </div>
            </template>
            <span v-else>管理員不可變更</span>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="group-admin">
      <h3>部門群組管理</h3>
      <p class="subtitle">可建立群組、指派成員加入群組，並將群組成員指定為主管。</p>
      <p v-if="props.groupsFeedback" class="feedback error">{{ props.groupsFeedback }}</p>

      <div class="row">
        <input v-model="createGroupNameModel" placeholder="新群組名稱" />
        <button @click="emit('createAdminGroup')">建立群組</button>
      </div>

      <div class="row">
        <select v-model="props.groupAssignForm.groupId">
          <option :value="null" disabled>選擇群組</option>
          <option v-for="g in props.adminGroups" :key="g.id" :value="g.id">{{ g.name }}</option>
        </select>
        <select v-model="props.groupAssignForm.memberId">
          <option :value="null" disabled>選擇成員</option>
          <option v-for="m in props.members" :key="m.id" :value="m.id">{{ m.employeeId }} {{ m.name }}</option>
        </select>
        <button @click="emit('addMemberToGroup')">加入群組</button>
      </div>

      <p v-if="props.loadingGroups">讀取群組中...</p>
      <ul v-else class="simple-list group-list">
        <li v-for="g in props.adminGroups" :key="g.id" class="group-card">
          <div class="group-head">
            <strong>{{ g.name }}</strong>
            <small>建立於 {{ new Date(g.createdAt).toLocaleString() }}</small>
          </div>
          <ul class="simple-list">
            <li v-for="gm in g.members" :key="`${g.id}-${gm.memberId}`" class="group-member-row">
              <span>
                {{ gm.employeeId }} {{ gm.name }} ({{ gm.role }})
                <strong v-if="gm.supervisor" class="group-supervisor-chip">主管</strong>
              </span>
              <div class="row">
                <button v-if="!gm.supervisor" @click="emit('setGroupSupervisor', g.id, gm.memberId)">設為主管</button>
                <button class="danger" @click="emit('removeMemberFromGroup', g.id, gm.memberId)">移出群組</button>
              </div>
            </li>
          </ul>
        </li>
      </ul>
    </div>

    <div class="group-admin">
      <h3>工單分類管理</h3>
      <p class="subtitle">ADMIN 可新增分類，使用者提交工單時需選擇分類。</p>
      <p v-if="props.categoryFeedback" class="feedback error">{{ props.categoryFeedback }}</p>

      <div class="row">
        <input v-model="createCategoryNameModel" placeholder="新分類名稱" />
        <button @click="emit('createHelpdeskCategory')">建立分類</button>
      </div>

      <ul class="simple-list">
        <li v-for="c in props.adminHelpdeskCategories" :key="c.id">
          <strong>{{ c.name }}</strong>
          <small> · 建立於 {{ new Date(c.createdAt).toLocaleString() }}</small>
        </li>
      </ul>
    </div>

    <div class="audit-admin">
      <div class="audit-head">
        <h3>操作紀錄（Audit Log）</h3>
        <div class="row">
          <button type="button" @click="emit('loadAuditLogs')">重新整理</button>
          <button type="button" :disabled="props.exportingAuditLogs" @click="emit('exportAuditLogsCsv')">
            {{ props.exportingAuditLogs ? '匯出中...' : '匯出 CSV' }}
          </button>
        </div>
      </div>
      <p class="subtitle">僅限 ADMIN 查詢。可依動作、實體、時間與操作者過濾。</p>
      <p v-if="props.auditLogsFeedback" class="feedback error">{{ props.auditLogsFeedback }}</p>
      <p v-if="props.auditCleanupFeedback" class="feedback">{{ props.auditCleanupFeedback }}</p>

      <div class="row">
        <label>清理幾天前的紀錄
          <input v-model.number="auditCleanupDaysModel" type="number" min="1" max="3650" />
        </label>
        <button type="button" class="danger" :disabled="props.purgingAuditLogs" @click="emit('purgeAuditLogs')">
          {{ props.purgingAuditLogs ? '清理中...' : '執行清理' }}
        </button>
      </div>

      <div class="audit-filters">
        <label>動作（action）
          <input v-model="props.auditFilters.action" placeholder="例如 TICKET_STATUS_UPDATED" />
        </label>
        <label>實體類型（entityType）
          <input v-model="props.auditFilters.entityType" placeholder="例如 TICKET / GROUP" />
        </label>
        <label>實體 ID（entityId）
          <input v-model="props.auditFilters.entityId" placeholder="例如 123" />
        </label>
        <label>操作者 ID（actorMemberId）
          <input v-model="props.auditFilters.actorMemberId" placeholder="例如 5" />
        </label>
        <label>起始時間（from）
          <input v-model="props.auditFilters.from" placeholder="2026-02-13T00:00:00Z" />
        </label>
        <label>結束時間（to）
          <input v-model="props.auditFilters.to" placeholder="2026-02-13T23:59:59Z" />
        </label>
        <label>筆數上限（1-500）
          <input v-model.number="props.auditFilters.limit" type="number" min="1" max="500" />
        </label>
        <button type="button" @click="emit('loadAuditLogs')">查詢</button>
      </div>

      <p v-if="props.loadingAuditLogs">讀取操作紀錄中...</p>
      <p v-else-if="!props.auditLogs.length">目前沒有符合條件的操作紀錄</p>
      <ul v-else class="simple-list audit-log-list">
        <li v-for="log in props.auditLogs" :key="log.id" class="audit-log-card">
          <div class="audit-log-title">
            <strong>#{{ log.id }} {{ log.action }}</strong>
            <small>{{ new Date(log.createdAt).toLocaleString() }}</small>
          </div>
          <small>
            {{ log.actorRole }} {{ log.actorName }} ({{ log.actorEmployeeId }})
            · entity: {{ log.entityType }} #{{ log.entityId ?? '-' }}
          </small>
          <details>
            <summary>前後資料與 metadata</summary>
            <div class="audit-log-json-grid">
              <div>
                <h4>Before</h4>
                <pre>{{ props.formatJsonPreview(log.beforeJson) }}</pre>
              </div>
              <div>
                <h4>After</h4>
                <pre>{{ props.formatJsonPreview(log.afterJson) }}</pre>
              </div>
              <div>
                <h4>Metadata</h4>
                <pre>{{ props.formatJsonPreview(log.metadataJson) }}</pre>
              </div>
            </div>
          </details>
        </li>
      </ul>
    </div>
  </section>
</template>
