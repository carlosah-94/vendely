package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.OpcionRequest;
import curso.fullstack.shopify.dto.OpcionResponse;
import curso.fullstack.shopify.dto.ProductoPublicoResponse;
import curso.fullstack.shopify.dto.ProductoRequest;
import curso.fullstack.shopify.dto.ProductoResponse;
import curso.fullstack.shopify.dto.VarianteRequest;
import curso.fullstack.shopify.dto.VarianteResponse;
import curso.fullstack.shopify.entity.ImagenProducto;
import curso.fullstack.shopify.entity.Inventario;
import curso.fullstack.shopify.entity.Precio;
import curso.fullstack.shopify.entity.Producto;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.entity.Variante;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.ImagenProductoRepository;
import curso.fullstack.shopify.repository.InventarioRepository;
import curso.fullstack.shopify.repository.PrecioRepository;
import curso.fullstack.shopify.repository.ProductoRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.repository.VarianteRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de productos (issue #10 · RF2 · rúbrica 1.2) y catálogo público.
 *
 * Un producto ocupa varias tablas: productos + variantes (con su SKU) + precios (con el precio
 * tachado opcional) + inventario + imagenes_producto + producto_categorias, y si tiene opciones
 * (talla, color) también producto_opciones y variante_valores.
 * - Producto simple: UNA variante sin valores.
 * - Producto con opciones: una variante por combinación ("M / Negro"), cada una con su precio y stock.
 * Por eso crear y editar van en una transacción: o se guarda todo, o nada.
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
    private final ImagenProductoRepository imagenProductoRepository;
    private final CategoriaService categoriaService;

    public ProductoService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
                           ProductoRepository productoRepository, VarianteRepository varianteRepository,
                           PrecioRepository precioRepository, InventarioRepository inventarioRepository,
                           ImagenProductoRepository imagenProductoRepository, CategoriaService categoriaService) {
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.precioRepository = precioRepository;
        this.inventarioRepository = inventarioRepository;
        this.imagenProductoRepository = imagenProductoRepository;
        this.categoriaService = categoriaService;
    }

    // ------------------------------------------------------------------ consultar (panel)

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.delDueno(yo, idTienda);
        return respuestas(productoRepository.filasDeTienda(idTienda));
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
        validarPrecioComparacion(r);
        String nombre = r.nombre().trim();
        String slug = slug(nombre);

        // Criterio del issue: un nombre repetido en la misma tienda se rechaza (en otra tienda sí puede).
        if (productoRepository.existsByIdTiendaAndNombreIgnoreCase(idTienda, nombre)
                || productoRepository.existsByIdTiendaAndSlug(idTienda, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay un producto con ese nombre en esta tienda");
        }

        List<Plan> planes = planificar(r);        // valida opciones y variantes antes de guardar nada

        Producto p = new Producto();
        p.setIdTienda(idTienda);
        copiarDatos(p, r, nombre, slug);
        productoRepository.saveAndFlush(p);       // flush: se necesita el id para las variantes

        crearOpciones(p.getIdProducto(), r.opciones());
        short posicion = 1;
        for (Plan plan : planes) {
            guardarVariante(p.getIdProducto(), null, plan, posicion++);
        }

        guardarImagenes(p, r.imagenes());
        categoriaService.asignar(idTienda, p.getIdProducto(), r.categorias());

        log.info("Producto creado: idProducto={}, tienda={}", p.getIdProducto(), idTienda);
        return fila(idTienda, p.getIdProducto());
    }

    // ------------------------------------------------------------------ editar

    @Transactional
    public ProductoResponse actualizar(UsuarioAutenticado yo, Long idTienda, Long idProducto, ProductoRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        Producto p = buscarEnTienda(idProducto, idTienda);
        validarPrecioComparacion(r);
        String nombre = r.nombre().trim();
        String slug = slug(nombre);

        // El nombre puede quedar igual; lo que no puede es repetir el de OTRO producto de la tienda.
        if (productoRepository.existsByIdTiendaAndNombreIgnoreCaseAndIdProductoNot(idTienda, nombre, idProducto)
                || productoRepository.existsByIdTiendaAndSlugAndIdProductoNot(idTienda, slug, idProducto)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay otro producto con ese nombre en esta tienda");
        }

        List<Plan> planes = planificar(r);
        List<Variante> actuales = varianteRepository.findByIdProductoOrderByIdVarianteAsc(idProducto);
        Map<Long, Variante> porId = actuales.stream().collect(Collectors.toMap(Variante::getIdVariante, v -> v));

        // Producto simple: se conserva su primera variante (la que ya tiene ventas e historial de precios).
        if (!tieneOpciones(r) && !actuales.isEmpty()) {
            planes = List.of(planes.get(0).conId(actuales.get(0).getIdVariante()));
        }
        // Las variantes enviadas con idVariante deben ser de este producto.
        for (Plan plan : planes) {
            if (plan.idVariante() != null && !porId.containsKey(plan.idVariante())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La variante " + plan.idVariante() + " no es de este producto");
            }
        }

        copiarDatos(p, r, nombre, slug);
        productoRepository.saveAndFlush(p);

        // Variantes que ya no se venden: se borran (con su precio y stock, en cascada). Si alguna ya
        // tiene ventas no se puede (detalle_pedido la necesita): se avisa sin cambiar nada.
        Set<Long> quedan = new HashSet<>();
        planes.forEach(plan -> quedan.add(plan.idVariante()));
        List<Variante> quitar = actuales.stream().filter(v -> !quedan.contains(v.getIdVariante())).toList();
        for (Variante v : quitar) {
            if (varianteRepository.tieneVentas(v.getIdVariante())) {
                throw new ApiException(HttpStatus.CONFLICT, "Una variante que quitaste ya tiene ventas y no se puede "
                        + "eliminar. Déjala con stock 0 para que no se venda más.");
            }
        }
        varianteRepository.deleteAll(quitar);
        varianteRepository.flush();

        // Opciones: se borran (con los valores de las variantes, en cascada) y se crean de nuevo en
        // el orden recibido. La base revisa al final de la transacción que cada variante tenga un
        // valor por opción y que no haya dos combinaciones iguales (trigger T5).
        varianteRepository.borrarOpciones(idProducto);
        varianteRepository.flush();
        crearOpciones(idProducto, r.opciones());

        short posicion = 1;
        for (Plan plan : planes) {
            guardarVariante(idProducto, plan.idVariante() == null ? null : porId.get(plan.idVariante()), plan, posicion++);
        }

        guardarImagenes(p, r.imagenes());
        categoriaService.asignar(idTienda, idProducto, r.categorias());

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
        List<ProductoRepository.Fila> filas = productoRepository.filasPublicas(t.getIdTienda());
        Detalle d = detalle(filas.stream().map(ProductoRepository.Fila::getIdProducto).toList());
        return filas.stream().map(f -> ProductoPublicoResponse.desde(f, d.opciones(f.getIdProducto()),
                d.variantesPublicas(f.getIdProducto()))).toList();
    }

    /** Detalle público de un producto: 404 si la tienda o el producto no existen o no están activos. */
    @Transactional(readOnly = true)
    public ProductoPublicoResponse productoPublico(String subdominio, Long idProducto) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        ProductoRepository.Fila f = productoRepository.filaPublica(t.getIdTienda(), idProducto)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El producto no existe"));
        Detalle d = detalle(List.of(idProducto));
        return ProductoPublicoResponse.desde(f, d.opciones(idProducto), d.variantesPublicas(idProducto));
    }

    // ------------------------------------------------------------------ apoyo

    private Producto buscarEnTienda(Long idProducto, Long idTienda) {
        return productoRepository.findByIdProductoAndIdTienda(idProducto, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El producto no existe en esta tienda"));
    }

    private ProductoResponse fila(Long idTienda, Long idProducto) {
        ProductoRepository.Fila f = productoRepository.filaDeTienda(idTienda, idProducto)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El producto no existe en esta tienda"));
        return respuestas(List.of(f)).get(0);
    }

    /** Respuestas del panel con las opciones y variantes de cada producto (2 consultas en total, no 2 por producto). */
    private List<ProductoResponse> respuestas(List<ProductoRepository.Fila> filas) {
        Detalle d = detalle(filas.stream().map(ProductoRepository.Fila::getIdProducto).toList());
        return filas.stream()
                .map(f -> ProductoResponse.desde(f, d.opciones(f.getIdProducto()), d.variantes(f.getIdProducto())))
                .toList();
    }

    /** Opciones y variantes de varios productos, agrupadas por producto. */
    private Detalle detalle(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return new Detalle(Map.of(), Map.of());
        }
        Map<Long, List<VarianteRepository.FilaOpcion>> opciones = varianteRepository.opcionesDeProductos(ids).stream()
                .collect(Collectors.groupingBy(VarianteRepository.FilaOpcion::getIdProducto, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<VarianteRepository.Fila>> variantes = varianteRepository.filasDeProductos(ids).stream()
                .collect(Collectors.groupingBy(VarianteRepository.Fila::getIdProducto, LinkedHashMap::new, Collectors.toList()));
        return new Detalle(opciones, variantes);
    }

    /** Separador de los valores en la consulta (carácter 31, "separador de unidades"). */
    private static final String SEPARADOR = String.valueOf((char) 31);

    private record Detalle(Map<Long, List<VarianteRepository.FilaOpcion>> opciones,
                           Map<Long, List<VarianteRepository.Fila>> variantes) {

        List<VarianteResponse> variantes(Long idProducto) {
            return variantes.getOrDefault(idProducto, List.of()).stream().map(v -> respuesta(v, v.getSku())).toList();
        }

        /** En la tienda pública no se muestra el SKU (es un dato interno del vendedor). */
        List<VarianteResponse> variantesPublicas(Long idProducto) {
            return variantes.getOrDefault(idProducto, List.of()).stream().map(v -> respuesta(v, null)).toList();
        }

        /** Cada opción con sus valores, en el orden en que aparecen en las variantes. */
        List<OpcionResponse> opciones(Long idProducto) {
            List<VarianteRepository.FilaOpcion> lista = opciones.getOrDefault(idProducto, List.of());
            List<VarianteResponse> vs = variantes(idProducto);
            List<OpcionResponse> resultado = new ArrayList<>();
            for (int i = 0; i < lista.size(); i++) {
                Set<String> valores = new java.util.LinkedHashSet<>();
                for (VarianteResponse v : vs) {
                    if (v.valores().size() > i) {
                        valores.add(v.valores().get(i));
                    }
                }
                resultado.add(new OpcionResponse(lista.get(i).getNombre(), List.copyOf(valores)));
            }
            return resultado;
        }

        private static VarianteResponse respuesta(VarianteRepository.Fila v, String sku) {
            List<String> valores = v.getValores() == null ? List.of() : List.of(v.getValores().split(SEPARADOR, -1));
            String nombre = valores.isEmpty() ? "Única" : String.join(" / ", valores);
            return new VarianteResponse(v.getIdVariante(), valores, nombre, v.getPrecio(), v.getPrecioComparacion(),
                    sku, v.getStock() == null ? 0 : v.getStock());
        }
    }

    // ------------------------------------------------------------------ opciones y variantes

    /**
     * Lo que se va a guardar de una variante, ya validado y con los valores por defecto puestos.
     * idVariante = null para una variante nueva.
     */
    private record Plan(Long idVariante, List<String> valores, BigDecimal precio, BigDecimal precioComparacion,
                        String sku, int stock) {
        Plan conId(Long id) {
            return new Plan(id, valores, precio, precioComparacion, sku, stock);
        }
    }

    private static boolean tieneOpciones(ProductoRequest r) {
        return r.opciones() != null && !r.opciones().isEmpty();
    }

    /**
     * Valida las opciones y variantes y arma la lista de lo que se va a guardar.
     * - Sin opciones: una sola variante con el precio, stock y SKU del producto.
     * - Con opciones: nombres de opción distintos, valores distintos dentro de cada opción,
     *   cada variante con un valor de cada opción y sin combinaciones repetidas (la base lo vuelve
     *   a revisar con el trigger T5, sin distinguir mayúsculas).
     */
    private static List<Plan> planificar(ProductoRequest r) {
        if (!tieneOpciones(r)) {
            return List.of(new Plan(null, List.of(), r.precio(), r.precioComparacion(), vacioANull(r.sku()), r.stock()));
        }
        Set<String> nombres = new HashSet<>();
        List<Set<String>> valoresPorOpcion = new ArrayList<>();
        for (OpcionRequest o : r.opciones()) {
            if (!nombres.add(o.nombre().trim().toLowerCase(Locale.ROOT))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La opción \"" + o.nombre().trim() + "\" está repetida");
            }
            Set<String> valores = new HashSet<>();
            for (String valor : o.valores()) {
                if (!valores.add(valor.trim().toLowerCase(Locale.ROOT))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El valor \"" + valor.trim() + "\" está repetido en " + o.nombre().trim());
                }
            }
            valoresPorOpcion.add(valores);
        }
        if (r.variantes() == null || r.variantes().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Agrega al menos una variante (una combinación de valores)");
        }

        Set<String> combinaciones = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        List<Plan> planes = new ArrayList<>();
        for (VarianteRequest v : r.variantes()) {
            if (v.valores().size() != r.opciones().size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cada variante necesita un valor por cada opción");
            }
            List<String> valores = v.valores().stream().map(String::trim).toList();
            for (int i = 0; i < valores.size(); i++) {
                if (!valoresPorOpcion.get(i).contains(valores.get(i).toLowerCase(Locale.ROOT))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "\"" + valores.get(i) + "\" no es un valor de "
                            + r.opciones().get(i).nombre().trim());
                }
            }
            String nombre = String.join(" / ", valores);
            if (!combinaciones.add(nombre.toLowerCase(Locale.ROOT))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La variante " + nombre + " está repetida");
            }
            if (v.idVariante() != null && !ids.add(v.idVariante())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La variante " + v.idVariante() + " está repetida");
            }
            BigDecimal precio = v.precio() != null ? v.precio() : r.precio();
            BigDecimal comparacion = v.precioComparacion() != null ? v.precioComparacion() : r.precioComparacion();
            if (comparacion != null && comparacion.compareTo(precio) <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "En " + nombre + ", el precio de comparación debe ser mayor que el precio");
            }
            planes.add(new Plan(v.idVariante(), valores, precio, comparacion, vacioANull(v.sku()),
                    v.stock() != null ? v.stock() : r.stock()));
        }
        return planes;
    }

    private void crearOpciones(Long idProducto, List<OpcionRequest> opciones) {
        if (opciones == null) {
            return;
        }
        short posicion = 1;
        for (OpcionRequest o : opciones) {
            varianteRepository.crearOpcion(idProducto, posicion++, o.nombre().trim());
        }
    }

    /**
     * Crea o actualiza una variante con sus valores, su precio y su stock.
     * existente = null para una variante nueva.
     */
    private void guardarVariante(Long idProducto, Variante existente, Plan plan, short posicion) {
        Variante v = existente != null ? existente : new Variante();
        v.setIdProducto(idProducto);
        v.setSku(plan.sku());
        v.setPosicion(posicion);
        varianteRepository.saveAndFlush(v);       // flush: se necesita el id para valores, precio e inventario
        Long idVariante = v.getIdVariante();

        short pos = 1;
        for (String valor : plan.valores()) {
            varianteRepository.crearValor(idVariante, idProducto, pos++, valor);
        }

        // Precio: la base no deja editar el monto ni el precio tachado (cada venta guarda el precio
        // con que se hizo). Si alguno cambió, se cierra el vigente y se crea uno nuevo.
        // Si son iguales, no se toca el historial.
        Precio actual = existente == null ? null : precioRepository.vigente(idVariante).orElse(null);
        if (actual == null
                || actual.getMonto().compareTo(plan.precio()) != 0
                || !mismoMonto(actual.getMontoComparacion(), plan.precioComparacion())) {
            if (actual != null) {
                precioRepository.cerrarVigente(idVariante);
                log.info("Precio cambiado: idVariante={}, {} -> {}", idVariante, actual.getMonto(), plan.precio());
            }
            crearPrecio(idVariante, plan.precio(), plan.precioComparacion());
        }

        // Stock: valor absoluto (para sumar mercadería que llega está Inventario → Recibir).
        Inventario i = inventarioRepository.findById(idVariante).orElseGet(() -> {
            Inventario nuevo = new Inventario();
            nuevo.setIdVariante(idVariante);
            return nuevo;
        });
        i.setCantidadDisponible(plan.stock());
        inventarioRepository.saveAndFlush(i);
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

    private void crearPrecio(Long idVariante, BigDecimal monto, BigDecimal montoComparacion) {
        Precio precio = new Precio();
        precio.setIdVariante(idVariante);
        precio.setMonto(monto);
        precio.setMontoComparacion(montoComparacion);
        precioRepository.saveAndFlush(precio);    // fecha_inicio la pone la base (now())
    }

    /**
     * El precio tachado ("antes") tiene que ser mayor que el precio actual; si no, no es una rebaja.
     * La base también lo exige (CHECK ck_precios_comparar), pero aquí se responde un 400 claro.
     */
    private static void validarPrecioComparacion(ProductoRequest r) {
        if (r.precioComparacion() != null && r.precio() != null
                && r.precioComparacion().compareTo(r.precio()) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El precio de comparación debe ser mayor que el precio");
        }
    }

    /** Compara dos montos que pueden ser null (49.9 y 49.90 cuentan como iguales). */
    private static boolean mismoMonto(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.compareTo(b) == 0;
    }

    /**
     * Guarda las fotos en el orden recibido (posición 1, 2, 3...). Se borran las anteriores y se
     * crean de nuevo: es lo más simple y evita choques con UNIQUE (id_producto, posicion).
     * null = el cliente no envió fotos: se dejan como están.
     */
    private void guardarImagenes(Producto p, List<String> imagenes) {
        if (imagenes == null) {
            return;
        }
        imagenProductoRepository.borrarDeProducto(p.getIdProducto());
        imagenProductoRepository.flush();
        short posicion = 1;
        for (String url : imagenes) {
            ImagenProducto im = new ImagenProducto();
            im.setIdProducto(p.getIdProducto());
            im.setUrl(url.trim());
            im.setTextoAlternativo(p.getNombre().length() > 200 ? p.getNombre().substring(0, 200) : p.getNombre());
            im.setPosicion(posicion++);
            imagenProductoRepository.save(im);
        }
        imagenProductoRepository.flush();
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
