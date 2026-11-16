<template>
  <v-app>
    <v-navigation-drawer v-model="drawer" app>
      <v-list-item>
        <v-list-item-content>
          <v-list-item-title class="text-h6">Balance Control</v-list-item-title>
          <v-list-item-subtitle>
            API <v-icon x-small :color="apiUp ? 'success' : 'error'">mdi-circle</v-icon>
          </v-list-item-subtitle>
        </v-list-item-content>
      </v-list-item>
      <v-divider />
      <v-list nav dense>
        <v-list-item v-for="link in links" :key="link.to" :to="link.to" exact>
          <v-list-item-icon><v-icon>{{ link.icon }}</v-icon></v-list-item-icon>
          <v-list-item-title>{{ link.text }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-navigation-drawer>

    <v-app-bar app color="primary" dark dense>
      <v-app-bar-nav-icon @click="drawer = !drawer" />
      <v-toolbar-title>Item balance and orders</v-toolbar-title>
    </v-app-bar>

    <v-main>
      <v-container fluid>
        <router-view />
      </v-container>
    </v-main>

    <v-snackbar v-model="notification.show" :color="notification.color" timeout="5000" bottom right>
      {{ notification.text }}
    </v-snackbar>
  </v-app>
</template>

<script>
import http from './api/http'
import { notification } from './notify'

export default {
  name: 'App',
  data: () => ({
    drawer: null,
    apiUp: false,
    notification,
    links: [
      { to: '/orders', text: 'Orders', icon: 'mdi-clipboard-list-outline' },
      { to: '/orders/new', text: 'New order', icon: 'mdi-cart-plus' },
      { to: '/items', text: 'Items', icon: 'mdi-package-variant-closed' },
      { to: '/dataload', text: 'Data load', icon: 'mdi-database-import-outline' }
    ]
  }),
  async created() {
    try {
      this.apiUp = (await http.get('/actuator/health')).data.status === 'UP'
    } catch (e) {
      this.apiUp = false
    }
  }
}
</script>
