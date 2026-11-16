<template>
  <v-app>
    <v-app-bar app color="primary" dark>
      <v-toolbar-title>Balance Control</v-toolbar-title>
    </v-app-bar>
    <v-main>
      <v-container>
        <router-view />
      </v-container>
    </v-main>
  </v-app>
</template>

<script>
export default {
  name: 'App'
}
</script>
