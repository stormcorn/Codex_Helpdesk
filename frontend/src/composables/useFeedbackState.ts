import { ref } from 'vue';
import type { FeedbackType } from '../types';

export function useTextFeedback(initial = '') {
  const feedback = ref(initial);

  function clearFeedback(): void {
    feedback.value = '';
  }

  function setFeedback(message: string): void {
    feedback.value = message;
  }

  return {
    feedback,
    clearFeedback,
    setFeedback
  };
}

export function useStatusFeedback() {
  const feedback = ref('');
  const feedbackType = ref<FeedbackType>('');

  function clearFeedback(): void {
    feedback.value = '';
    feedbackType.value = '';
  }

  function setError(message: string): void {
    feedback.value = message;
    feedbackType.value = 'error';
  }

  function setSuccess(message: string): void {
    feedback.value = message;
    feedbackType.value = 'success';
  }

  return {
    feedback,
    feedbackType,
    clearFeedback,
    setError,
    setSuccess
  };
}
