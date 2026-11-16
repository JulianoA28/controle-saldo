import Vue from 'vue'

/** Minimal global snackbar state (no Vuex needed for one message at a time). */
export const notification = Vue.observable({ show: false, text: '', color: 'success' })

export function notify(text, color = 'success') {
  notification.text = text
  notification.color = color
  notification.show = true
}

export function notifyError(error) {
  notify(error && error.message ? error.message : 'Unexpected error.', 'error')
}
