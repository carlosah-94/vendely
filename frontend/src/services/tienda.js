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
/**
 * Pide el código de verificación para registrarse como cliente de esta tienda.
 * Devuelve { requerido, correo, venceEnSegundos, reenviarEnSegundos }; requerido = false si el
 * backend tiene la verificación apagada (entonces la cuenta se crea sin código).
 */
export async function pedirCodigoCliente(correo, subdominio) {
  return (await api.post('/auth/registro/codigo', { correo, subdominio })).data;
}

/** datos lleva codigoVerificacion (el de 6 dígitos que llegó al correo, o null si no se pide). */
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

/**
 * ¿Sirve este código de descuento para un carrito con este subtotal? Devuelve
 * { codigo, tipoDescuento, valor, descuento }, o un error 400 con el motivo. Es una vista previa:
 * al crear el pedido (datos.codigoCupon) el backend lo vuelve a revisar.
 */
export async function validarCupon(codigo, subtotal) {
  return (await api.post('/cliente/cupones/validar', { codigo, subtotal })).data;
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
export async function descargarMiComprobante(idPedido, numeroPedido, numeroComprobante) {
  return descargarPdf(`/cliente/pedidos/${idPedido}/comprobante`,
    `${numeroComprobante ?? `comprobante-${numeroPedido ?? idPedido}`}.pdf`);
}
