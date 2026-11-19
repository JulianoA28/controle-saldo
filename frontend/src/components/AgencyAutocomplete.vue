<template>
  <v-autocomplete
    :value="value"
    :items="agencies"
    :loading="loading"
    :search-input.sync="search"
    :label="label"
    :item-value="itemValue"
    :item-text="agencyText"
    :clearable="clearable"
    :rules="rules"
    no-filter
    hide-no-data
    @input="onInput"
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
  data: () => ({ agencies: [], selected: null, loading: false, search: null, timer: null }),
  watch: {
    value(v) {
      if (v == null) this.selected = null
    },
    search(text) {
      // Selecting an agency fills the search box with its label: that is not a new search.
      if (this.selected && text === this.agencyText(this.selected)) return
      clearTimeout(this.timer)
      this.timer = setTimeout(() => this.load(text), 250)
    }
  },
  created() {
    this.load('')
  },
  methods: {
    agencyText: (a) => `${a.acronym} — ${a.name}`,
    onInput(value) {
      this.selected = this.agencies.find((a) => a[this.itemValue] === value) || null
      this.$emit('input', value)
    },
    async load(text) {
      this.loading = true
      try {
        const found = (await agenciesApi.search(text || '', 50)).content
        // No cache-items (with no-filter it would keep showing stale results): keep only the
        // selected agency, so its label stays visible.
        const keep = this.selected && !found.some((a) => a.id === this.selected.id) ? [this.selected] : []
        this.agencies = keep.concat(found)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
