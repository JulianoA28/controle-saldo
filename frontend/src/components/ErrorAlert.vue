<template>
  <v-alert v-if="error" type="error" outlined dense dismissible class="mb-4" @input="$emit('dismiss')">
    <div class="font-weight-medium">{{ error.message }}</div>
    <div class="text-caption">{{ error.code }}</div>
    <ul v-if="lines.length" class="mt-1">
      <li v-for="(line, i) in lines" :key="i">{{ line }}</li>
    </ul>
  </v-alert>
</template>

<script>
import { describeDetail } from '../utils/format'

/** Renders the backend error contract { code, message, details }. */
export default {
  name: 'ErrorAlert',
  props: { error: { type: Object, default: null } },
  computed: {
    lines() {
      return this.error && Array.isArray(this.error.details) ? this.error.details.map(describeDetail) : []
    }
  }
}
</script>
