<template>
  <div>
    <h1 class="text-h5 mb-4">Legacy data load</h1>

    <v-alert v-if="summary && !summary.loaded" type="info" outlined>
      No load has run yet. Put the CSVs (or the zip) in <code>data/</code> and run <code>docker compose up</code>.
    </v-alert>

    <template v-if="summary && summary.loaded">
      <v-row>
        <v-col v-for="card in cards" :key="card.label" cols="6" md="3">
          <v-card outlined>
            <v-card-text>
              <div class="text-caption">{{ card.label }}</div>
              <div class="text-h5">{{ formatNumber(card.value) }}</div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
      <div class="text-caption mb-4">Finished at {{ formatDateTime(summary.finishedAt) }}</div>

      <v-tabs v-model="tab">
        <v-tab>Quarantine</v-tab>
        <v-tab>Balance divergences</v-tab>
      </v-tabs>
      <v-tabs-items v-model="tab">
        <v-tab-item>
          <v-card flat>
            <v-card-text>
              <v-chip-group v-model="selectedReason" column active-class="primary--text" @change="reloadRejections">
                <v-chip v-for="r in summary.rejections" :key="r.source + r.reason" :value="r" filter outlined small>
                  {{ r.source }} · {{ r.reason }} · {{ formatNumber(r.count) }}
                </v-chip>
              </v-chip-group>
            </v-card-text>
            <v-data-table
              :headers="rejectionHeaders"
              :items="rejections.content"
              :options.sync="rejectionOptions"
              :server-items-length="rejections.totalElements"
              :loading="loadingRejections"
              :footer-props="{ itemsPerPageOptions: [10, 20, 50, 100] }"
              dense
              @update:options="loadRejections"
            >
              <template #[`item.raw`]="{ item }"><code class="raw">{{ compact(item.raw) }}</code></template>
            </v-data-table>
          </v-card>
        </v-tab-item>
        <v-tab-item>
          <v-card flat>
            <v-card-text>
              The legacy <strong>current balance</strong> was kept as the source of truth. These items differ from
              <em>initial balance − confirmed orders</em>; red ones would be negative if recalculated.
              See <code>docs/adr/0002-legacy-data-load.md</code>.
              <v-switch v-model="onlyNegative" label="Only items that would become negative" @change="reloadDivergences" />
            </v-card-text>
            <v-data-table
              :headers="divergenceHeaders"
              :items="divergences.content"
              :options.sync="divergenceOptions"
              :server-items-length="divergences.totalElements"
              :loading="loadingDivergences"
              :footer-props="{ itemsPerPageOptions: [10, 20, 50, 100] }"
              dense
              @update:options="loadDivergences"
            >
              <template #[`item.computedBalance`]="{ item }">
                <span :class="{ 'error--text font-weight-bold': item.computedBalance < 0 }">
                  {{ formatNumber(item.computedBalance) }}
                </span>
              </template>
            </v-data-table>
          </v-card>
        </v-tab-item>
      </v-tabs-items>
    </template>
  </div>
</template>

<script>
import { dataloadApi } from '../api'
import { notifyError } from '../notify'
import { formatDateTime, formatNumber } from '../utils/format'

export default {
  name: 'DataloadView',
  data: () => ({
    summary: null,
    tab: 0,
    selectedReason: null,
    rejections: { content: [], totalElements: 0 },
    rejectionOptions: { page: 1, itemsPerPage: 20 },
    loadingRejections: false,
    rejectionHeaders: [
      { text: 'File', value: 'source', sortable: false },
      { text: 'Line', value: 'lineNo', sortable: false },
      { text: 'Reason', value: 'reason', sortable: false },
      { text: 'Key', value: 'sourceKey', sortable: false },
      { text: 'Original row', value: 'raw', sortable: false }
    ],
    onlyNegative: false,
    divergences: { content: [], totalElements: 0 },
    divergenceOptions: { page: 1, itemsPerPage: 20 },
    loadingDivergences: false,
    divergenceHeaders: [
      { text: 'Item', value: 'itemCode', sortable: false },
      { text: 'Initial', value: 'initialBalance', sortable: false, align: 'end' },
      { text: 'Confirmed orders', value: 'confirmedQuantity', sortable: false, align: 'end' },
      { text: 'Recalculated', value: 'computedBalance', sortable: false, align: 'end' },
      { text: 'Legacy (kept)', value: 'legacyBalance', sortable: false, align: 'end' }
    ]
  }),
  computed: {
    cards() {
      const s = this.summary.stats
      return [
        { label: 'Agencies', value: s.agencies },
        { label: 'Items', value: s.items },
        { label: 'Orders', value: s.orders },
        { label: 'Order lines', value: s.orderLines },
        { label: 'Rejected rows', value: s.rejections },
        { label: 'Items with divergent balance', value: s.divergentItems },
        { label: 'Would be negative if recalculated', value: s.negativeIfRecalculated }
      ]
    }
  },
  async created() {
    try {
      this.summary = await dataloadApi.summary()
    } catch (e) {
      notifyError(e)
    }
  },
  methods: {
    formatDateTime,
    formatNumber,
    compact(raw) {
      return Object.values(raw).map((v) => (v === null ? '∅' : v)).join(' ; ')
    },
    async loadRejections() {
      this.loadingRejections = true
      try {
        const filter = this.selectedReason ? { source: this.selectedReason.source, reason: this.selectedReason.reason } : {}
        this.rejections = await dataloadApi.rejections(this.rejectionOptions, filter)
      } catch (e) {
        notifyError(e)
      } finally {
        this.loadingRejections = false
      }
    },
    reloadRejections() {
      if (this.rejectionOptions.page !== 1) this.rejectionOptions = { ...this.rejectionOptions, page: 1 }
      else this.loadRejections()
    },
    async loadDivergences() {
      this.loadingDivergences = true
      try {
        this.divergences = await dataloadApi.divergences(this.divergenceOptions, { onlyNegative: this.onlyNegative })
      } catch (e) {
        notifyError(e)
      } finally {
        this.loadingDivergences = false
      }
    },
    reloadDivergences() {
      if (this.divergenceOptions.page !== 1) this.divergenceOptions = { ...this.divergenceOptions, page: 1 }
      else this.loadDivergences()
    }
  }
}
</script>

<style scoped>
.raw {
  font-size: 11px;
  white-space: nowrap;
}
</style>
