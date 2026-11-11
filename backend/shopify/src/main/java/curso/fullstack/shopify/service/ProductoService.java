package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.ProductoPublicoResponse;
import curso.fullstack.shopify.dto.ProductoRequest;
import curso.fullstack.shopify.dto.ProductoResponse;
import curso.fullstack.shopify.entity.Inventario;
import curso.fullstack.shopify.entity.Precio;
import curso.fullstack.shopify.entity.Producto;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.entity.Variante;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.InventarioRepository;
import curso.fullstack.shopify.repository.PrecioRepository;
import curso.fullstack.shopify.repository.ProductoRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.repository.VarianteRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de productos simples (issue #10 · RF2 · rúbrica 1.2) y catálogo público.
 *
 * Un producto simple ocupa CUATRO tablas: productos + variantes (una) + precios + inventario.
 * Por eso crear y editar van en una transacción: o se guardan las cuatro, o ninguna.
 * Todas las operaciones del panel empiezan con verificadorTienda.delDueno(...): un dueño solo
 * gestiona los productos de SUS tiendas (403 si la tienda es de otro, 404 si no existe).
 */
@Slf4j
@Service
public class ProductoService {

    private static final short ACTIVO = 1;
    private static final short BORRADOR = 0;

    private final VerificadorTienda verificadorTienda;
    private final TiendaRepository tiendaRepository;
    private final ProductoRepository productoRepository;
    private final VarianteRepository varianteRepository;
    private final PrecioRepository precioRepository;
    private final InventarioRepository inventarioRepository;

    public ProductoService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
                           ProductoRepository productoRepository, VarianteRepository varianteRepository,
                           PrecioRepository precioRepository, InventarioRepository inventarioRepository) {
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.precioRepository = precioRepository;
        this.inventarioRepository = inventarioRepository;
    }

    // ------------------------------------------------------------------ consultar (panel)

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.delDueno(yo, idTienda);
        return productoRepository.filasDeTienda(idTienda).stream().map(ProductoResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse ver(UsuarioAutenticado yo, Long idTienda, Long idProducto) {
        verificadorTienda.delDueno(yo, idTienda);
        return fila(idTienda, idProducto);
    }

    // ------------------------------------------------------------------ crear

    /** Crea producto + variante + precio + stock en una sola transacción. */
    @Transactional
    public ProductoResponse crear(UsuarioAutenticado yo, Long idTienda, ProductoRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        String nombre = r.nombre().trim();
        String slug = slug(nombre);

        // Criterio del issue: un nombre repetido en la misma tienda se rechaza (en otra tienda sí puede).
        if (productoRepository.existsByIdTiendaAndNombreIgnoreCase(idTienda, nombre)
                || productoRepository.existsByIdTiendaAndSlug(idTienda, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay un producto con ese nombre en esta tienda");
        }

        Producto p = new Producto();
        p.setIdTienda(idTienda);
        copiarDatos(p, r, nombre, slug);
        productoRepository.saveAndFlush(p);       // flush: se necesita el id para la variante

        // Producto simple: una sola variante, sin opciones (en ventas se muestra como "Única").
        Variante v = new Variante();
        v.setIdProducto(p.getIdProducto());
        varianteRepository.saveAndFlush(v);       // flush: se necesita el id para precio e inventario

        crearPrecio(v.getIdVariante(), r.precio());

        Inventario i = new Inventario();
        i.setIdVariante(v.getIdVariante());
        i.setCantidadDisponible(r.stock());
        inventarioRepository.saveAndFlush(i);

        log.info("Producto creado: idProducto={}, tienda={}", p.getIdProducto(), idTienda);
        return fila(idTienda, p.getIdProducto());
    }

    // ------------------------------------------------------------------ editar

    @Transactional
    public ProductoResponse actualizar(UsuarioAutenticado yo, Long idTienda, Long idProducto, ProductoRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        Producto p = buscarEnTienda(idProducto, idTienda);
        String nombre = r.nombre().trim();
        String slug = slug(nombre);

        // El nombre puede quedar igual; lo que no puede es repetir el de OTRO producto de la tienda.
        if (productoRepository.existsByIdTiendaAndNombreIgnoreCaseAndIdProductoNot(idTienda, nombre, idProducto)
                || productoRepository.existsByIdTiendaAndSlugAndIdProductoNot(idTienda, slug, idProducto)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay otro producto con ese nombre en esta tienda");
        }

        // Este CRUD maneja productos simples. Uno con tallas/colores tiene un precio y un stock por
        // variante: no se sabría a cuál aplicar el precio y el stock enviados.
        List<Variante> variantes = varianteRepository.findByIdProducto(idProducto);
        if (variantes.size() != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Este producto tiene varias variantes: su precio y stock se gestionan por variante");
        }
        Long idVariante = variantes.get(0).getIdVariante();

        copiarDatos(p, r, nombre, slug);
        productoRepository.saveAndFlush(p);

        // Precio: la base no deja editar el monto (cada venta guarda el precio con que se hizo).
        // Si cambió, se cierra el vigente y se crea uno nuevo. Si es igual, no se toca el historial.
        BigDecimal actual = precioRepository.vigente(idVariante).map(Precio::getMonto).orElse(null);
        if (actual == null || actual.compareTo(r.precio()) != 0) {
            precioRepository.cerrarVigente(idVariante);
            crearPrecio(idVariante, r.precio());
            log.info("Precio cambiado: idProducto={}, {} -> {}", idProducto, actual, r.precio());
        }

        // Stock: valor absoluto. Si por algún motivo no tenía fila de inventario, se crea.
        Inventario i = inventarioRepository.findById(idVariante).orElseGet(() -> {
            Inventario nuevo = new Inventario();
            nuevo.setIdVariante(idVariante);
            return nuevo;
        });
        i.setCantidadDisponible(r.stock());
        inventarioRepository.saveAndFlush(i);

        log.info("Producto actualizado: idProducto={}, tienda={}", idProducto, idTienda);
        return fila(idTienda, idProducto);
    }

    // ------------------------------------------------------------------ eliminar

    /**
     * Elimina un producto. Si ya se vendió, sus variantes están en detalle_pedido y la base lo
     * impediría con un error genérico: se revisa antes y se responde 409 con un mensaje claro
     * (para ocultarlo sin borrarlo, se puede editar con "activo": false).
     * Sin ventas, al borrar el producto la base borra en cascada su variante, precios e inventario.
     */
    @Transactional
    public void eliminar(UsuarioAutenticado yo, Long idTienda, Long idProducto) {
        verificadorTienda.delDueno(yo, idTienda);
        Producto p = buscarEnTienda(idProducto, idTienda);

        if (productoRepository.tieneVentas(idProducto)) {
            log.info("Eliminación de producto rechazada, tiene ventas: idProducto={}, tienda={}", idProducto, idTienda);
            throw new ApiException(HttpStatus.CONFLICT,
                    "El producto ya tiene ventas: no se puede eliminar (puedes desactivarlo)");
        }
        productoRepository.delete(p);
        productoRepository.flush();   // si la base lo rechazara, el error sale aquí y se deshace todo
        log.info("Producto eliminado: idProducto={}, tienda={}", idProducto, idTienda);
    }

    // ------------------------------------------------------------------ catálogo público

    /**
     * Catálogo de una tienda para visitantes (sin token). Solo si la tienda está activa y solo
     * productos activos: una tienda suspendida o un borrador no deben verse.
     */
    @Transactional(readOnly = true)
    public List<ProductoPublicoResponse> catalogo(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        return productoRepository.filasPublicas(t.getIdTienda()).stream().map(ProductoPublicoResponse::desde).toList();
    }

    /** Detalle público de un producto: 404 si la tienda o el producto no existen o no están activos. */
    @Transactional(readOnly = true)
    public ProductoPublicoResponse productoPublico(String subdominio, Long idProducto) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        return productoRepository.filaPublica(t.getIdTienda(), idProducto).map(ProductoPublicoResponse::desde)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El producto no existe"));
    }

    // ------------------------------------------------------------------ apoyo

    private Producto buscarEnTienda(Long idProducto, Long idTienda) {
        return productoRepository.findByIdProductoAndIdTienda(idProducto, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El producto no existe en esta tienda"));
    }

    private ProductoResponse fila(Long idTienda, Long idProducto) {
        return productoRepository.filaDeTienda(idTienda, idProducto).map(ProductoResponse::desde)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El producto no existe en esta tienda"));
    }

    private void copiarDatos(Producto p, ProductoRequest r, String nombre, String slug) {
        p.setNombre(nombre);
        p.setSlug(slug);
        p.setDescripcion(vacioANull(r.descripcion()));
        p.setMarca(vacioANull(r.marca()));
        // null o true = activo. Se crean activos para que aparezcan en el catálogo y se puedan vender
        // (la base, por defecto, los crearía como borrador).
        p.setEstado(Boolean.FALSE.equals(r.activo()) ? BORRADOR : ACTIVO);
    }

    private void crearPrecio(Long idVariante, BigDecimal monto) {
        Precio precio = new Precio();
        precio.setIdVariante(idVariante);
        precio.setMonto(monto);
        precioRepository.saveAndFlush(precio);    // fecha_inicio la pone la base (now())
    }

    /**
     * Texto para la URL a partir del nombre: "Polo Básico" → "polo-basico".
     * Quita tildes, pasa a minúsculas y cambia todo lo que no sea letra o número por guiones,
     * para cumplir el CHECK de la base (^[a-z0-9]+(-[a-z0-9]+)*$).
     */
    static String slug(String nombre) {
        String s = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")            // quita las tildes (á → a, ñ → n)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")       // espacios y símbolos → guion
                .replaceAll("(^-+|-+$)", "");        // sin guiones al inicio ni al final
        if (s.isEmpty()) {
            s = "producto";
        }
        return s.length() > 220 ? s.substring(0, 220).replaceAll("-+$", "") : s;
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
