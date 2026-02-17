<script setup lang="ts">
import type { FeedbackType, HelpdeskCategory, MyGroup, TicketForm } from '../types';

const props = defineProps<{
  ticketForm: TicketForm;
  myGroups: MyGroup[];
  helpdeskCategories: HelpdeskCategory[];
  selectedFiles: File[];
  submittingTicket: boolean;
  ticketFeedback: string;
  ticketFeedbackType: FeedbackType;
  formatSize: (bytes: number) => string;
}>();

defineEmits<{
  submit: [];
  filesChanged: [event: Event];
}>();
</script>

<template>
  <section class="panel">
    <h2>新增工單</h2>
    <form class="form-grid" @submit.prevent="$emit('submit')">
      <label>姓名<input v-model="props.ticketForm.name" required /></label>
      <label>Email<input v-model="props.ticketForm.email" type="email" required /></label>
      <label>所屬群組
        <select v-model="props.ticketForm.groupId" required>
          <option :value="null" disabled>請選擇群組</option>
          <option v-for="g in props.myGroups" :key="g.id" :value="g.id">{{ g.name }}</option>
        </select>
      </label>
      <label>工單分類
        <select v-model="props.ticketForm.categoryId" required>
          <option :value="null" disabled>請選擇分類</option>
          <option v-for="c in props.helpdeskCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </label>
      <label>主旨<input v-model="props.ticketForm.subject" required /></label>
      <label>優先層級
        <select v-model="props.ticketForm.priority">
          <option value="GENERAL">一般</option>
          <option value="URGENT">急件（需主管確認）</option>
        </select>
      </label>
      <label>問題描述<textarea v-model="props.ticketForm.description" rows="5" required /></label>
      <label>附件（可多檔，每檔 < 5MB）<input type="file" multiple @change="$emit('filesChanged', $event)" /></label>
      <ul v-if="props.selectedFiles.length" class="simple-list">
        <li v-for="f in props.selectedFiles" :key="f.name + f.lastModified">{{ f.name }} ({{ props.formatSize(f.size) }})</li>
      </ul>
      <button :disabled="props.submittingTicket" type="submit">{{ props.submittingTicket ? '送出中...' : '送出工單' }}</button>
    </form>
    <p v-if="props.ticketFeedback" class="feedback" :class="props.ticketFeedbackType">{{ props.ticketFeedback }}</p>
  </section>
</template>
