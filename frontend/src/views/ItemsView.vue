<template>
  <div>
    <div class="d-flex align-center mb-4">
      <h1 class="text-h5">Items</h1>
      <v-spacer />
      <v-btn color="primary" @click="openCreate"><v-icon left>mdi-plus</v-icon>New item</v-btn>
    </div>

    <v-card>
      <v-card-title class="pb-0">
        <v-row dense>
          <v-col cols="12" sm="8">
            <v-text-field v-model="search" prepend-inner-icon="mdi-magnify" label="Search by code or description"
                          clearable hide-details dense outlined @input="debouncedReload" />
          </v-col>
          <v-col cols="12" sm="4">
            <v-select v-model="active" :items="activeOptions" label="Status" hide-details dense outlined @change="reload" />
          </v-col>
        </v-row>
      </v-card-title>
      <v-data-table
        :headers="headers"
        :items="page.content"
        :options.sync="options"
        :server-items-length="page.totalElements"
        :loading="loading"
        :footer-props="{ itemsPerPageOptions: [10, 20, 50, 100] }"
        @update:options="load"
      >
        <template #[`item.balance`]="{ item }">{{ formatNumber(item.balance) }}</template>
        <template #[`item.active`]="{ item }">
          <v-chip small :color="item.active ? 'success' : 'grey'" text-color="white">
            {{ item.active ? 'Active' : 'Inactive' }}
          </v-chip>
        </template>
        <template #[`item.actions`]="{ item }">
          <v-btn icon small title="Edit" @click="openEdit(item)"><v-icon small>mdi-pencil</v-icon></v-btn>
          <v-btn icon small title="Adjust balance" @click="openAdjust(item)"><v-icon small>mdi-plus-minus-variant</v-icon></v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Create -->
    <v-dialog v-model="createDialog" max-width="520">
      <v-card>
        <v-card-title>New item</v-card-title>
        <v-card-text>
          <error-alert :error="createError" @dismiss="createError = null" />
          <v-form ref="createForm">
            <v-text-field v-model="createForm.code" label="Code" :rules="[required, max(20)]" />
            <v-text-field v-model="createForm.description" label="Description" :rules="[required, max(200)]" />
            <v-text-field v-model="createForm.unit" label="Unit (e.g. UN, CX)" :rules="[required, max(10)]" />
            <v-text-field v-model.number="createForm.initialBalance" type="number" min="0" label="Initial balance"
                          :rules="[nonNegativeInt]" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="createDialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="saving" @click="create">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Edit (R5: optimistic locking) -->
    <v-dialog v-model="editDialog" max-width="560" persistent>
      <v-card v-if="editing">
        <v-card-title>Edit {{ editing.code }}</v-card-title>
        <v-card-subtitle>Version {{ editing.version }} · balance {{ formatNumber(editing.balance) }}
          (change it with "Adjust balance")</v-card-subtitle>
        <v-card-text>
          <v-alert v-if="conflict" type="warning" outlined>
            <div class="font-weight-medium mb-2">Someone else changed this item while you were editing.</div>
            <v-simple-table dense>
              <thead>
                <tr><th /><th>Your values</th><th>Current values (v{{ conflict.version }})</th></tr>
              </thead>
              <tbody>
                <tr v-for="f in conflictFields" :key="f.key" :class="{ 'warning lighten-4': f.mine !== f.theirs }">
                  <td>{{ f.label }}</td><td>{{ f.mine }}</td><td>{{ f.theirs }}</td>
                </tr>
              </tbody>
            </v-simple-table>
            <div class="mt-2 d-flex">
              <v-btn small text @click="discardMine">Discard mine, load current</v-btn>
              <v-spacer />
              <v-btn small color="warning" @click="reapplyMine">Reapply mine on current</v-btn>
            </div>
          </v-alert>
          <error-alert :error="editError" @dismiss="editError = null" />
          <v-form ref="editForm">
            <v-text-field v-model="editForm.description" label="Description" :rules="[required, max(200)]" />
            <v-text-field v-model="editForm.unit" label="Unit" :rules="[required, max(10)]" />
            <v-switch v-model="editForm.active" label="Active (can be ordered)" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="editDialog = false">Close</v-btn>
          <v-btn color="primary" :loading="saving" :disabled="!!conflict" @click="saveEdit">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Balance adjustment -->
    <v-dialog v-model="adjustDialog" max-width="640">
      <v-card v-if="adjusting">
        <v-card-title>Adjust balance · {{ adjusting.code }}</v-card-title>
        <v-card-subtitle>Current balance: {{ formatNumber(adjusting.balance) }} {{ adjusting.unit }}</v-card-subtitle>
        <v-card-text>
          <error-alert :error="adjustError" @dismiss="adjustError = null" />
          <v-form ref="adjustForm">
            <v-text-field v-model.number="adjustForm.delta" type="number" label="Delta (+ entry, − write-off)"
                          :rules="[nonZeroInt]" />
            <v-text-field v-model="adjustForm.reason" label="Reason" :rules="[required, max(200)]" />
          </v-form>
          <div class="text-subtitle-2 mt-2">History</div>
          <v-data-table dense :headers="adjustmentHeaders" :items="adjustments" :items-per-page="5"
                        no-data-text="No adjustments yet">
            <template #[`item.createdAt`]="{ item }">{{ formatDateTime(item.createdAt) }}</template>
          </v-data-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="adjustDialog = false">Close</v-btn>
          <v-btn color="primary" :loading="saving" @click="adjust">Apply</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import ErrorAlert from '../components/ErrorAlert.vue'
import { itemsApi } from '../api'
import { notify, notifyError } from '../notify'
import { formatDateTime, formatNumber } from '../utils/format'

const isInt = (v) => v !== null && v !== undefined && String(v).trim() !== '' && Number.isInteger(Number(v))

export default {
  name: 'ItemsView',
  components: { ErrorAlert },
  data: () => ({
    headers: [
      { text: 'Code', value: 'code', sortable: false },
      { text: 'Description', value: 'description', sortable: false },
      { text: 'Unit', value: 'unit', sortable: false },
      { text: 'Balance', value: 'balance', sortable: false, align: 'end' },
      { text: 'Status', value: 'active', sortable: false },
      { text: '', value: 'actions', sortable: false, align: 'end' }
    ],
    adjustmentHeaders: [
      { text: 'When', value: 'createdAt', sortable: false },
      { text: 'Delta', value: 'delta', sortable: false, align: 'end' },
      { text: 'Balance after', value: 'balanceAfter', sortable: false, align: 'end' },
      { text: 'Reason', value: 'reason', sortable: false }
    ],
    activeOptions: [
      { text: 'All', value: null },
      { text: 'Active', value: true },
      { text: 'Inactive', value: false }
    ],
    page: { content: [], totalElements: 0 },
    options: { page: 1, itemsPerPage: 20 },
    search: '',
    active: null,
    loading: false,
    saving: false,
    timer: null,
    createDialog: false,
    createForm: {},
    createError: null,
    editDialog: false,
    editing: null,
    editForm: {},
    editError: null,
    conflict: null,
    adjustDialog: false,
    adjusting: null,
    adjustForm: {},
    adjustError: null,
    adjustments: []
  }),
  computed: {
    conflictFields() {
      if (!this.conflict) return []
      return [
        { key: 'description', label: 'Description', mine: this.editForm.description, theirs: this.conflict.description },
        { key: 'unit', label: 'Unit', mine: this.editForm.unit.trim().toUpperCase(), theirs: this.conflict.unit },
        { key: 'active', label: 'Active', mine: String(this.editForm.active), theirs: String(this.conflict.active) }
      ]
    }
  },
  methods: {
    formatNumber,
    formatDateTime,
    required: (v) => (v !== null && v !== undefined && String(v).trim() !== '') || 'Required',
    max: (n) => (v) => !v || String(v).length <= n || `At most ${n} characters`,
    // Vuetify validates its own copy of the input, which stays a string ("0") when the typed
    // value equals the current model value; so accept numeric strings, not only numbers.
    nonNegativeInt: (v) => (isInt(v) && Number(v) >= 0) || 'Whole number ≥ 0',
    nonZeroInt: (v) => (isInt(v) && Number(v) !== 0) || 'Whole number, not zero',

    async load() {
      this.loading = true
      try {
        this.page = await itemsApi.list(this.options, { search: this.search || undefined, active: this.active ?? undefined })
      } catch (e) {
        notifyError(e)
      } finally {
        this.loading = false
      }
    },
    reload() {
      if (this.options.page !== 1) this.options = { ...this.options, page: 1 }
      else this.load()
    },
    debouncedReload() {
      clearTimeout(this.timer)
      this.timer = setTimeout(this.reload, 300)
    },
    replaceRow(item) {
      const i = this.page.content.findIndex((row) => row.id === item.id)
      if (i >= 0) this.$set(this.page.content, i, item)
    },

    openCreate() {
      this.createForm = { code: '', description: '', unit: 'UN', initialBalance: 0 }
      this.createError = null
      this.createDialog = true
    },
    async create() {
      if (!this.$refs.createForm.validate()) return
      this.saving = true
      try {
        const item = await itemsApi.create(this.createForm)
        notify(`Item ${item.code} created`)
        this.createDialog = false
        this.reload()
      } catch (e) {
        this.createError = e
      } finally {
        this.saving = false
      }
    },

    openEdit(item) {
      this.editing = item
      this.editForm = { description: item.description, unit: item.unit, active: item.active }
      this.editError = null
      this.conflict = null
      this.editDialog = true
    },
    async saveEdit() {
      if (!this.$refs.editForm.validate()) return
      this.saving = true
      try {
        const saved = await itemsApi.update(this.editing.id, { ...this.editForm, version: this.editing.version })
        this.replaceRow(saved)
        notify(`Item ${saved.code} saved`)
        this.editDialog = false
      } catch (e) {
        if (e.code === 'ITEM_VERSION_CONFLICT' && e.details && e.details[0]) {
          // Nothing is overwritten: show both versions and let the user decide.
          this.conflict = e.details[0]
          this.replaceRow(this.conflict)
        } else {
          this.editError = e
        }
      } finally {
        this.saving = false
      }
    },
    discardMine() {
      this.openEdit(this.conflict)
    },
    reapplyMine() {
      // Keep the user's values but take the current version: the next save is a conscious overwrite.
      this.editing = this.conflict
      this.conflict = null
    },

    async openAdjust(item) {
      this.adjusting = item
      this.adjustForm = { delta: null, reason: '' }
      this.adjustError = null
      this.adjustments = []
      this.adjustDialog = true
      this.loadAdjustments()
    },
    async loadAdjustments() {
      try {
        this.adjustments = (await itemsApi.adjustments(this.adjusting.id, { page: 1, itemsPerPage: 50 })).content
      } catch (e) {
        notifyError(e)
      }
    },
    async adjust() {
      if (!this.$refs.adjustForm.validate()) return
      this.saving = true
      try {
        const item = await itemsApi.adjust(this.adjusting.id, this.adjustForm)
        this.adjusting = item
        this.replaceRow(item)
        this.adjustForm = { delta: null, reason: '' }
        this.$refs.adjustForm.resetValidation()
        notify(`New balance of ${item.code}: ${formatNumber(item.balance)}`)
        this.loadAdjustments()
      } catch (e) {
        this.adjustError = e
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
