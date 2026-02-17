<script setup lang="ts">
const props = defineProps<{
  open: boolean;
  src: string;
  title: string;
}>();

const emit = defineEmits<{
  close: [];
}>();
</script>

<template>
  <div v-if="props.open" class="lightbox" @click.self="emit('close')">
    <div class="lightbox-body">
      <button @click="emit('close')">關閉</button>
      <img :src="props.src" :alt="props.title" />
      <p>{{ props.title }}</p>
    </div>
  </div>
</template>
