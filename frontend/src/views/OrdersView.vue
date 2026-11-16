<template>
  <div>
    <div class="d-flex align-center mb-4">
      <h1 class="text-h5">Orders</h1>
      <v-spacer />
      <v-btn color="primary" :to="{ name: 'new-order' }"><v-icon left>mdi-cart-plus</v-icon>New order</v-btn>
    </div>

    <v-card>
      <v-card-title class="pb-0">
        <v-row dense>
          <v-col cols="12" sm="5">
            <agency-autocomplete v-model="agencyId" item-value="id" label="Agency" clearable @input="reload" />
          </v-col>
          <v-col cols="12" sm="3">
            <v-select v-model="status" :items="statusOptions" label="Status" hide-details dense outlined @change="reload" />
          </v-col>
          <v-col cols="12" sm="4">
            <v-text-field v-model="externalId" label="External id (exact)" clearable hide-details dense outlined
                          :error="!!externalId && !uuidPattern.test(externalId)" @input="debouncedReload" />
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
        @click:row="open"
      >
        <template #[`item.agency`]="{ item }">{{ item.agencyAcronym }} <span class="grey--text">({{ item.agencyCode }})</span></template>
        <template #[`item.createdAt`]="{ item }">{{ formatDateTime(item.createdAt) }}</template>
        <template #[`item.status`]="{ item }"><status-chip :status="item.status" /></template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="detailDialog" max-width="760">
      <v-card v-if="detail">
        <v-card-title>
          Order #{{ detail.id }}
          <v-spacer />
          <status-chip :status="detail.status" />
        </v-card-title>
        <v-card-subtitle class="mt-1">
          External id <code>{{ detail.externalId }}</code>
        </v-card-subtitle>
        <v-card-text>
          <error-alert :error="detailError" @dismiss="detailError = null" />
          <v-row dense>
            <v-col cols="12" sm="6"><strong>Agency:</strong> {{ detail.agency.acronym }} — {{ detail.agency.name }}</v-col>
            <v-col cols="12" sm="6"><strong>Requester:</strong> {{ detail.requester }} ({{ detail.origin }})</v-col>
            <v-col cols="12" sm="6"><strong>Created:</strong> {{ formatDateTime(detail.createdAt) }}</v-col>
            <v-col v-if="detail.cancelledAt" cols="12" sm="6">
              <strong>Cancelled:</strong> {{ formatDateTime(detail.cancelledAt) }}
              <span v-if="detail.cancellationReason">— {{ detail.cancellationReason }}</span>
            </v-col>
          </v-row>
          <v-simple-table dense class="mt-3">
            <thead><tr><th>Item</th><th>Description</th><th class="text-right">Quantity</th></tr></thead>
            <tbody>
              <tr v-for="line in detail.items" :key="line.itemId">
                <td>{{ line.itemCode }}</td><td>{{ line.description }}</td>
                <td class="text-right">{{ formatNumber(line.quantity) }} {{ line.unit }}</td>
              </tr>
            </tbody>
          </v-simple-table>
          <v-expand-transition>
            <div v-if="confirmingCancel" class="mt-4">
              <v-text-field v-model="cancelReason" label="Cancellation reason (optional)" counter="200" />
              <div class="text-caption">The quantities above go back to the item balances.</div>
            </div>
          </v-expand-transition>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="detailDialog = false">Close</v-btn>
          <template v-if="detail.status === 'CONFIRMED'">
            <v-btn v-if="!confirmingCancel" color="error" text @click="confirmingCancel = true">Cancel order</v-btn>
            <v-btn v-else color="error" :loading="cancelling" @click="cancel">Confirm cancellation</v-btn>
          </template>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import AgencyAutocomplete from '../components/AgencyAutocomplete.vue'
import ErrorAlert from '../components/ErrorAlert.vue'
import StatusChip from '../components/StatusChip.vue'
import { ordersApi } from '../api'
import { notify, notifyError } from '../notify'
import { formatDateTime, formatNumber } from '../utils/format'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export default {
  name: 'OrdersView',
  components: { AgencyAutocomplete, ErrorAlert, StatusChip },
  data: () => ({
    headers: [
      { text: '#', value: 'id', sortable: false },
      { text: 'Created', value: 'createdAt', sortable: false },
      { text: 'Agency', value: 'agency', sortable: false },
      { text: 'Requester', value: 'requester', sortable: false },
      { text: 'Origin', value: 'origin', sortable: false },
      { text: 'Status', value: 'status', sortable: false }
    ],
    statusOptions: [
      { text: 'All', value: null },
      { text: 'Confirmed', value: 'CONFIRMED' },
      { text: 'Cancelled', value: 'CANCELLED' }
    ],
    uuidPattern: UUID,
    page: { content: [], totalElements: 0 },
    options: { page: 1, itemsPerPage: 20 },
    agencyId: null,
    status: null,
    externalId: '',
    loading: false,
    timer: null,
    detailDialog: false,
    detail: null,
    detailError: null,
    confirmingCancel: false,
    cancelReason: '',
    cancelling: false
  }),
  methods: {
    formatDateTime,
    formatNumber,
    async load() {
      const externalId = this.externalId && UUID.test(this.externalId.trim()) ? this.externalId.trim() : undefined
      if (this.externalId && !externalId) return
      this.loading = true
      try {
        this.page = await ordersApi.list(this.options, {
          agencyId: this.agencyId || undefined,
          status: this.status || undefined,
          externalId
        })
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
    async open(row) {
      this.detailError = null
      this.confirmingCancel = false
      this.cancelReason = ''
      try {
        this.detail = await ordersApi.get(row.id)
        this.detailDialog = true
      } catch (e) {
        notifyError(e)
      }
    },
    async cancel() {
      this.cancelling = true
      try {
        this.detail = await ordersApi.cancel(this.detail.id, this.cancelReason || null)
        this.confirmingCancel = false
        notify(`Order #${this.detail.id} cancelled; balances restored`)
        this.load()
      } catch (e) {
        this.detailError = e
        if (e.code === 'ORDER_ALREADY_CANCELLED') {
          this.detail = await ordersApi.get(this.detail.id)
          this.load()
        }
      } finally {
        this.cancelling = false
      }
    }
  }
}
</script>
