import { api } from './api';

/**
 * Llamadas de la tienda pública y de la cuenta del cliente. Devuelven directamente los datos
 * (response.data); los errores se dejan pasar para que la pantalla los muestre.
 */

// ---- Públicas (sin token)
export async function obtenerTienda(subdominio) {
  return (await api.get(`/public/tiendas/${subdominio}`)).data;
}

export async function listarProductos(subdominio) {
  return (await api.get(`/public/tiendas/${subdominio}/productos`)).data;
}

export async function obtenerProducto(subdominio, idProducto) {
  return (await api.get(`/public/tiendas/${subdominio}/productos/${idProducto}`)).data;
}

// ---- Registro de cliente
export async function registrarCliente(datos) {
  return (await api.post('/auth/registro/cliente', datos)).data;
}

// ---- Cliente con sesión (rol CLIENTE)
export async function listarDirecciones() {
  return (await api.get('/cliente/direcciones')).data;
}

export async function crearDireccion(datos) {
  return (await api.post('/cliente/direcciones', datos)).data;
}

export async function crearPedido(datos) {
  return (await api.post('/cliente/pedidos', datos)).data;
}

export async function iniciarPago(idPedido) {
  return (await api.post(`/cliente/pedidos/${idPedido}/pago`)).data;
}

export async function confirmarPago(idPedido, paymentId) {
  return (await api.post(`/cliente/pedidos/${idPedido}/pago/confirmar`, { paymentId })).data;
}

export async function listarMisPedidos() {
  return (await api.get('/cliente/pedidos')).data;
}

export async function verMiPedido(idPedido) {
  return (await api.get(`/cliente/pedidos/${idPedido}`)).data;
}
