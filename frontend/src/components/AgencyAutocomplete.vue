<template>
  <v-autocomplete
    :value="value"
    :items="agencies"
    :loading="loading"
    :search-input.sync="search"
    :label="label"
    :item-value="itemValue"
    :item-text="(a) => `${a.acronym} — ${a.name}`"
    :clearable="clearable"
    :rules="rules"
    no-filter
    hide-no-data
    cache-items
    @input="$emit('input', $event)"
  />
</template>

<script>
import { agenciesApi } from '../api'

/** Server-side search: there are few agencies, but the component should not assume it. */
export default {
  name: 'AgencyAutocomplete',
  props: {
    value: { type: [String, Number], default: null },
    label: { type: String, default: 'Agency' },
    itemValue: { type: String, default: 'code' },
    clearable: { type: Boolean, default: false },
    rules: { type: Array, default: () => [] }
  },
  data: () => ({ agencies: [], loading: false, search: null, timer: null }),
  watch: {
    search(text) {
      clearTimeout(this.timer)
      this.timer = setTimeout(() => this.load(text), 250)
    }
  },
  created() {
    this.load('')
  },
  methods: {
    async load(text) {
      this.loading = true
      try {
        this.agencies = (await agenciesApi.search(text || '', 50)).content
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
