<template>
  <div>
    <h1 class="text-h5 mb-4">New order</h1>
    <v-card max-width="900">
      <v-card-text>
        <error-alert :error="error" @dismiss="error = null" />
        <v-form ref="form">
          <v-row dense>
            <v-col cols="12" sm="7">
              <agency-autocomplete v-model="agencyCode" :rules="[required]" />
            </v-col>
            <v-col cols="12" sm="5">
              <v-text-field v-model="requester" label="Requester" :rules="[required, max(100)]" />
            </v-col>
          </v-row>

          <div class="text-subtitle-1 mt-2">Items</div>
          <v-row v-for="(line, i) in lines" :key="line.key" dense align="center">
            <v-col cols="12" sm="8">
              <v-autocomplete
                v-model="line.item"
                :items="line.options"
                :loading="line.loading"
                :search-input.sync="line.search"
                :item-text="itemText"
                return-object
                no-filter
                hide-no-data
                label="Item"
                :rules="[required, notRepeated(i)]"
                @focus="line.options.length || searchItems(line, '')"
                @update:search-input="searchItems(line, $event)"
              />
            </v-col>
            <v-col cols="9" sm="3">
              <v-text-field v-model.number="line.quantity" type="number" min="1" label="Quantity"
                            :suffix="line.item ? line.item.unit : ''" :rules="[positiveInt]" />
            </v-col>
            <v-col cols="3" sm="1" class="text-right">
              <v-btn icon :disabled="lines.length === 1" title="Remove line" @click="lines.splice(i, 1)">
                <v-icon>mdi-delete</v-icon>
              </v-btn>
            </v-col>
          </v-row>
          <v-btn text color="primary" @click="addLine"><v-icon left>mdi-plus</v-icon>Add item</v-btn>
        </v-form>
      </v-card-text>
      <v-divider />
      <v-card-actions>
        <span class="text-caption grey--text ml-2">
          Idempotency key: <code>{{ externalId }}</code> — retrying never creates a second order.
        </span>
        <v-spacer />
        <v-btn text @click="reset">Clear</v-btn>
        <v-btn color="primary" :loading="submitting" @click="submit">Place order</v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>

<script>
import AgencyAutocomplete from '../components/AgencyAutocomplete.vue'
import ErrorAlert from '../components/ErrorAlert.vue'
import { itemsApi, ordersApi } from '../api'
import { notify } from '../notify'
import { uuid } from '../utils/uuid'

let lineKey = 0
const newLine = () => ({ key: ++lineKey, item: null, quantity: 1, options: [], loading: false, search: null, timer: null })

export default {
  name: 'NewOrderView',
  components: { AgencyAutocomplete, ErrorAlert },
  data: () => ({
    // R6 from the portal side: one key per order being composed. A double click or a retry
    // after a timeout resends the same key, and the backend answers with the same order.
    externalId: uuid(),
    agencyCode: null,
    requester: '',
    lines: [newLine()],
    submitting: false,
    error: null
  }),
  methods: {
    required: (v) => (v !== null && v !== undefined && String(v).trim() !== '') || 'Required',
    max: (n) => (v) => !v || String(v).length <= n || `At most ${n} characters`,
    positiveInt: (v) => (Number.isInteger(v) && v > 0) || 'Whole number > 0',
    notRepeated(index) {
      return (item) => !item || !this.lines.some((l, j) => j !== index && l.item && l.item.id === item.id)
        || 'Item already in this order'
    },
    addLine() {
      this.lines.push(newLine())
    },
    itemText: (it) => `${it.code} — ${it.description} (${it.balance} ${it.unit})`,
    searchItems(line, text) {
      // Selecting an item fills the search box with its label: that is not a new search.
      if (line.item && text === this.itemText(line.item)) return
      clearTimeout(line.timer)
      line.timer = setTimeout(async () => {
        line.loading = true
        try {
          const found = (await itemsApi.list({ page: 1, itemsPerPage: 20 }, { search: text || undefined, active: true })).content
          // No cache-items (with no-filter it would keep showing stale results): keep only the
          // selected item, so its label stays visible.
          const selected = line.item && !found.some((it) => it.id === line.item.id) ? [line.item] : []
          line.options = selected.concat(found)
        } finally {
          line.loading = false
        }
      }, 250)
    },
    async submit() {
      if (!this.$refs.form.validate()) return
      this.submitting = true
      this.error = null
      try {
        const { order, created } = await ordersApi.place({
          externalId: this.externalId,
          agencyCode: this.agencyCode,
          requester: this.requester,
          origin: 'PORTAL',
          items: this.lines.map((l) => ({ itemCode: l.item.code, quantity: l.quantity }))
        })
        notify(created ? `Order #${order.id} placed` : `Order #${order.id} was already registered (resend)`,
          created ? 'success' : 'info')
        this.reset()
      } catch (e) {
        // Keep the same key: if the first attempt actually reached the server, the retry is safe.
        this.error = e
      } finally {
        this.submitting = false
      }
    },
    reset() {
      this.externalId = uuid()
      this.agencyCode = null
      this.requester = ''
      this.lines = [newLine()]
      this.error = null
      this.$nextTick(() => this.$refs.form.resetValidation())
    }
  }
}
</script>
