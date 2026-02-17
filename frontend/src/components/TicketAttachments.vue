<script setup lang="ts">
import type { Attachment } from '../types';

const props = defineProps<{
  ticketId: number;
  attachments: Attachment[];
  isImageAttachment: (attachment: Attachment) => boolean;
}>();

const emit = defineEmits<{
  openImageLightbox: [ticketId: number, attachment: Attachment];
  downloadAttachment: [ticketId: number, attachment: Attachment];
}>();
</script>

<template>
  <ul v-if="props.attachments.length" class="simple-list">
    <li v-for="att in props.attachments" :key="att.id">
      <template v-if="props.isImageAttachment(att)">
        <button class="link-button" type="button" @click="emit('openImageLightbox', props.ticketId, att)">預覽 {{ att.originalFilename }}</button>
      </template>
      <template v-else>
        <button class="link-button" type="button" @click="emit('downloadAttachment', props.ticketId, att)">下載 {{ att.originalFilename }}</button>
      </template>
    </li>
  </ul>
</template>
