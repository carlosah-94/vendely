package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Oferta;
import curso.fullstack.shopify.repository.OfertaRepository;
import curso.fullstack.shopify.repository.ProductoRepository;
import curso.fullstack.shopify.service.CalculoOferta;
import java.math.BigDecimal;
import java.util.List;

/**
 * Producto para el catálogo público (issue #10), lo que ve un visitante sin iniciar sesión.
 * Es un DTO aparte del panel porque no muestra datos internos (estado, SKU). El stock se envía para
 * que la tienda limite la cantidad del carrito; el backend vuelve a validarlo al crear el pedido.
 */
public record ProductoPublicoResponse(
        Long idProducto,
        String nombre,
        String slug,
        String descripcion,
        String marca,
        Long idVariante,
        BigDecimal precio,
        Long stock,
        boolean disponible,            // true si hay stock
        String imagenUrl,              // primera imagen, o null si no tiene
        BigDecimal precioComparacion,  // precio "antes" (tachado), o null
        List<String> imagenes,         // todas las fotos en orden
        List<Long> categorias,         // ids de sus categorías (la tienda filtra por ellas)
        List<OpcionResponse> opciones, // vacía en un producto simple
        List<VarianteResponse> variantes,  // cada combinación con su precio y stock (sin SKU)
        OfertaPublica oferta,              // oferta automática vigente, o null
        String preguntaCliente             // la tienda pide un dato al comprar (V19), o null
) {
    /**
     * Oferta automática vigente del producto. Con oferta, "precio" ya viene rebajado y
     * "precioComparacion" es el precio de antes (el que se ve tachado).
     * tipoDescuento: PORCENTAJE o MONTO_FIJO.
     */
    public record OfertaPublica(String nombre, String tipoDescuento, BigDecimal valor) {}

    /**
     * El mismo producto con el precio rebajado por la oferta (también en cada variante).
     * El precio tachado es el de antes de la oferta, o el precio de comparación si era mayor.
     */
    public ProductoPublicoResponse conOferta(OfertaRepository.Vigente o, int decimales) {
        if (o == null) {
            return this;
        }
        List<VarianteResponse> rebajadas = variantes.stream().map(v -> v.precio() == null ? v
                : new VarianteResponse(v.idVariante(), v.valores(), v.nombre(), rebajar(o, v.precio(), decimales),
                        tachado(v.precio(), v.precioComparacion()), null, v.sku(), v.stock())).toList();
        OfertaPublica datos = new OfertaPublica(o.getNombre(),
                o.getTipoDescuento() == Oferta.PORCENTAJE ? "PORCENTAJE" : "MONTO_FIJO", o.getValor());
        return new ProductoPublicoResponse(idProducto, nombre, slug, descripcion, marca, idVariante,
                precio == null ? null : rebajar(o, precio, decimales), stock, disponible, imagenUrl,
                precio == null ? precioComparacion : tachado(precio, precioComparacion),
                imagenes, categorias, opciones, rebajadas, datos, preguntaCliente);
    }

    private static BigDecimal rebajar(OfertaRepository.Vigente o, BigDecimal precio, int decimales) {
        return precio.subtract(CalculoOferta.descuento(o, precio, decimales));
    }

    private static BigDecimal tachado(BigDecimal precio, BigDecimal comparacion) {
        return comparacion != null && comparacion.compareTo(precio) > 0 ? comparacion : precio;
    }

    public static ProductoPublicoResponse desde(ProductoRepository.Fila f, List<OpcionResponse> opciones,
                                                List<VarianteResponse> variantes) {
        long stock = f.getStock() == null ? 0 : f.getStock();
        return new ProductoPublicoResponse(f.getIdProducto(), f.getNombre(), f.getSlug(), f.getDescripcion(),
                f.getMarca(), f.getIdVariante(), f.getPrecio(), stock, stock > 0, f.getImagenUrl(),
                f.getPrecioComparacion(), ProductoResponse.separarImagenes(f.getImagenes()),
                ProductoResponse.separarIds(f.getCategorias()), opciones, variantes, null, f.getPreguntaCliente());
    }
}
