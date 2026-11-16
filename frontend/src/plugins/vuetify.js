import Vue from 'vue'
import Vuetify from 'vuetify'
// Prebuilt CSS instead of Vuetify's SASS sources: avoids pinning the legacy sass 1.32 toolchain.
import 'vuetify/dist/vuetify.min.css'
import '@mdi/font/css/materialdesignicons.css'

Vue.use(Vuetify)

export default new Vuetify({
  icons: { iconfont: 'mdi' }
})
