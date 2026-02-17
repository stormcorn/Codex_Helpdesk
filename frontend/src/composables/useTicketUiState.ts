import { reactive, ref, type Ref } from 'vue';
import type { Attachment, FeedbackType, TicketForm } from '../types';

type UseTicketUiStateOptions = {
  token: Ref<string>;
};

export function useTicketUiState(options: UseTicketUiStateOptions) {
  const ticketForm = reactive<TicketForm>({
    name: '',
    email: '',
    subject: '',
    description: '',
    priority: 'GENERAL',
    groupId: null,
    categoryId: null
  });
  const selectedFiles = ref<File[]>([]);
  const ticketFeedback = ref('');
  const ticketFeedbackType = ref<FeedbackType>('');
  const replyInputs = reactive<Record<number, string>>({});
  const openTicketIds = reactive<Record<number, boolean>>({});

  const lightboxOpen = ref(false);
  const lightboxSrc = ref('');
  const lightboxTitle = ref('');

  function formatSize(bytes: number): string {
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
  }

  function onFilesChanged(event: Event): void {
    const input = event.target as HTMLInputElement;
    selectedFiles.value = Array.from(input.files ?? []);
  }

  function attachmentViewUrl(ticketId: number, attachmentId: number): string {
    return `/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/view?token=${encodeURIComponent(options.token.value)}`;
  }

  function attachmentDownloadUrl(ticketId: number, attachmentId: number): string {
    return `/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/download?token=${encodeURIComponent(options.token.value)}`;
  }

  function isImageAttachment(attachment: Attachment): boolean {
    return attachment.contentType.startsWith('image/');
  }

  function openImageLightbox(ticketId: number, attachment: Attachment): void {
    lightboxSrc.value = attachmentViewUrl(ticketId, attachment.id);
    lightboxTitle.value = attachment.originalFilename;
    lightboxOpen.value = true;
  }

  function closeLightbox(): void {
    lightboxOpen.value = false;
    lightboxSrc.value = '';
    lightboxTitle.value = '';
  }

  function toggleTicket(ticketId: number): void {
    openTicketIds[ticketId] = !openTicketIds[ticketId];
  }

  function openTicket(ticketId: number): void {
    openTicketIds[ticketId] = true;
  }

  function onReplyInput(ticketId: number, value: string): void {
    replyInputs[ticketId] = value;
  }

  function clearTicketUiState(): void {
    selectedFiles.value = [];
    ticketFeedback.value = '';
    ticketFeedbackType.value = '';
    closeLightbox();
    Object.keys(replyInputs).forEach((key) => delete replyInputs[Number(key)]);
    Object.keys(openTicketIds).forEach((key) => delete openTicketIds[Number(key)]);
  }

  return {
    ticketForm,
    selectedFiles,
    ticketFeedback,
    ticketFeedbackType,
    replyInputs,
    openTicketIds,
    lightboxOpen,
    lightboxSrc,
    lightboxTitle,
    formatSize,
    onFilesChanged,
    attachmentDownloadUrl,
    isImageAttachment,
    openImageLightbox,
    closeLightbox,
    toggleTicket,
    openTicket,
    onReplyInput,
    clearTicketUiState
  };
}
