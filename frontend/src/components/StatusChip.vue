<template>
  <v-chip small :color="status === 'CONFIRMED' ? 'success' : 'grey'" text-color="white">
    {{ status === 'CONFIRMED' ? 'Confirmed' : 'Cancelled' }}
  </v-chip>
</template>

<script>
export default {
  name: 'StatusChip',
  props: { status: { type: String, required: true } }
}
</script>
