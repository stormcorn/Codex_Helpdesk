<script setup lang="ts">
import type { Member, Role } from '../types';

const props = defineProps<{
  open: boolean;
  loadingMembers: boolean;
  membersFeedback: string;
  members: Member[];
  onUpdateMemberRole: (member: Member, role: Role) => void;
  onDeleteMember: (member: Member) => void;
}>();
</script>

<template>
  <section v-if="props.open" class="panel">
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
                <button @click="props.onUpdateMemberRole(m, 'USER')">設為 USER</button>
                <button @click="props.onUpdateMemberRole(m, 'IT')">設為 IT</button>
                <button class="danger" @click="props.onDeleteMember(m)">刪除</button>
              </div>
            </template>
            <span v-else>管理員不可變更</span>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
