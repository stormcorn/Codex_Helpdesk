<script setup lang="ts">
import type { FeedbackType, TicketForm } from '../types';

const props = defineProps<{
  open: boolean;
  ticketForm: TicketForm;
  selectedFiles: File[];
  submittingTicket: boolean;
  ticketFeedback: string;
  ticketFeedbackType: FeedbackType;
  onFilesChanged: (event: Event) => void;
  onSubmit: () => void;
  formatSize: (bytes: number) => string;
}>();
</script>

<template>
  <section v-if="props.open" class="panel">
    <h2>新增工單</h2>
    <form class="form-grid" @submit.prevent="props.onSubmit">
      <label>姓名<input v-model="props.ticketForm.name" required /></label>
      <label>Email<input v-model="props.ticketForm.email" type="email" required /></label>
      <label>主旨<input v-model="props.ticketForm.subject" required /></label>
      <label>問題描述<textarea v-model="props.ticketForm.description" rows="5" required /></label>
      <label>附件（可多檔，每檔 < 5MB）<input type="file" multiple @change="props.onFilesChanged" /></label>
      <ul v-if="props.selectedFiles.length" class="simple-list">
        <li v-for="f in props.selectedFiles" :key="f.name + f.lastModified">{{ f.name }} ({{ props.formatSize(f.size) }})</li>
      </ul>
      <button :disabled="props.submittingTicket" type="submit">{{ props.submittingTicket ? '送出中...' : '送出工單' }}</button>
    </form>
    <p v-if="props.ticketFeedback" class="feedback" :class="props.ticketFeedbackType">{{ props.ticketFeedback }}</p>
  </section>
</template>
