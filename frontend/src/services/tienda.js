import { api, descargarPdf } from './api';

/**
 * Llamadas de la tienda pública y de la cuenta del cliente. Devuelven directamente los datos
 * (response.data); los errores se dejan pasar para que la pantalla los muestre.
 */

// ---- Públicas (sin token)
export async function obtenerTienda(subdominio) {
  return (await api.get(`/public/tiendas/${subdominio}`)).data;
}

/**
 * Catalogo de la tienda. Devuelve una pagina: { contenido, pagina, tamano, total, paginas, hayMas }.
 *
 * Solo se mandan los filtros con valor: una URL con ?q=&min= no filtra nada y ensucia el enlace
 * que el visitante puede copiar.
 */
export async function listarProductos(subdominio, filtros = {}) {
  const parametros = new URLSearchParams();
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor !== undefined && valor !== null && valor !== '' && valor !== false) {
      parametros.set(clave, valor);
    }
  }
  const cadena = parametros.toString();
  return (await api.get(`/public/tiendas/${subdominio}/productos${cadena ? `?${cadena}` : ''}`)).data;
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

/**
 * Inicia el pago con la pasarela elegida ('PAYPAL' o 'MERCADO_PAGO') y devuelve { urlPago }.
 * Sin método, el backend usa el primero que ofrece la tienda.
 */
export async function iniciarPago(idPedido, metodo) {
  const cuerpo = metodo ? { metodo } : undefined;
  return (await api.post(`/cliente/pedidos/${idPedido}/pago`, cuerpo)).data;
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

/** Descarga el comprobante de compra de uno de mis pedidos (issue #3). */
export async function descargarMiComprobante(idPedido, numeroPedido) {
  return descargarPdf(`/cliente/pedidos/${idPedido}/comprobante`,
    `comprobante-${numeroPedido ?? idPedido}.pdf`);
}
