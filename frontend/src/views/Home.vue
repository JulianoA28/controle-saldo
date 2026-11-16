<template>
  <v-card max-width="480">
    <v-card-title>Backend status</v-card-title>
    <v-card-text>
      <v-progress-circular v-if="loading" indeterminate size="24" />
      <v-chip v-else :color="status === 'UP' ? 'success' : 'error'" text-color="white">
        {{ status }}
      </v-chip>
      <div v-if="error" class="mt-2 error--text">{{ error }}</div>
    </v-card-text>
    <v-card-actions>
      <v-btn text color="primary" :disabled="loading" @click="load">Refresh</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script>
import http from '../api/http'

export default {
  name: 'Home',
  data() {
    return { loading: false, status: '', error: '' }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.get('/actuator/health')
        this.status = data.status
      } catch (e) {
        this.status = 'DOWN'
        this.error = e.message
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
