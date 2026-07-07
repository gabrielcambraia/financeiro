import axios from 'axios'

const cliente = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

export default cliente
