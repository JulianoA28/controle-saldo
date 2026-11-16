import Vue from 'vue'
import VueRouter from 'vue-router'
import ItemsView from '../views/ItemsView.vue'
import OrdersView from '../views/OrdersView.vue'
import NewOrderView from '../views/NewOrderView.vue'
import DataloadView from '../views/DataloadView.vue'

Vue.use(VueRouter)

export default new VueRouter({
  mode: 'history',
  routes: [
    { path: '/', redirect: '/orders' },
    { path: '/orders', name: 'orders', component: OrdersView },
    { path: '/orders/new', name: 'new-order', component: NewOrderView },
    { path: '/items', name: 'items', component: ItemsView },
    { path: '/dataload', name: 'dataload', component: DataloadView },
    { path: '*', redirect: '/orders' }
  ]
})
