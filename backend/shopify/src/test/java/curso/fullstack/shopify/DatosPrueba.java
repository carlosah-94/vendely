package curso.fullstack.shopify;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Crea los datos que necesita cada prueba directamente con SQL (issue #9).
 *
 * Cada llamada a {@link #nuevaTienda()} arma un mundo propio: dueño, tienda en Perú (plan Gratis),
 * un cliente con su dirección en Miraflores y tarifas de envío. Así ninguna prueba depende del
 * orden en que corren ni de lo que dejaron las otras, aunque compartan la misma base.
 *
 * Se usa SQL y no la API para que una prueba de pagos no falle por un error en el alta de
 * productos: cada prueba ejercita solo lo que dice su nombre.
 */
public class DatosPrueba {

    /** La contraseña de todas las cuentas que se crean aquí. */
    public static final String CONTRASENA = "Prueba1234";

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private final JdbcTemplate jdbc;
    private final String hash;

    public DatosPrueba(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.hash = passwordEncoder.encode(CONTRASENA);   // BCrypt es lento: se calcula una sola vez
    }

    /** Una tienda con todo lo necesario para vender. */
    public record Tienda(long idTienda, String subdominio, long idDueno, long idCliente, long idDireccion,
                         String correoDueno, String correoCliente) {
    }

    public Tienda nuevaTienda() {
        int n = CONTADOR.incrementAndGet();
        String sufijo = n + "-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);

        long idDueno = nuevaCuenta(2, null, "dueno" + sufijo);
        // RUC (tipo 2): 11 dígitos que empiezan con 20. El contador lo hace único.
        String ruc = "20" + String.format("%09d", (System.nanoTime() + n) % 1_000_000_000L);
        Long idPropietario = jdbc.queryForObject("""
                INSERT INTO propietarios (id_login, id_rol, tipo_documento, numero_documento)
                VALUES (?, 2, 2, ?) RETURNING id_propietario""", Long.class, idDueno, ruc);

        String subdominio = "prueba-" + sufijo;
        Long idTienda = jdbc.queryForObject("""
                INSERT INTO tiendas (id_propietario, id_pais, nombre, subdominio, correo_contacto)
                VALUES (?, 'PE', ?, ?, ?) RETURNING id_tienda""", Long.class,
                idPropietario, "Tienda " + sufijo, subdominio, "ventas" + sufijo + "@prueba.pe");

        // Sin tarifa la base rechaza la venta (trigger tg_pedidos_preparar).
        jdbc.update("""
                INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
                SELECT ?, id_division, 10.00, NULL, 1, 5 FROM divisiones WHERE id_pais = 'PE' AND nivel = 1""", idTienda);

        long idCliente = nuevoCliente(idTienda);
        long idDireccion = nuevaDireccion(idCliente);
        return new Tienda(idTienda, subdominio, idDueno, idCliente, idDireccion,
                correoDe(idDueno), correoDe(idCliente));
    }

    /** Un cliente más de la tienda, con su dirección; devuelve su id_login. */
    public long nuevoCliente(long idTienda) {
        return nuevaCuenta(3, idTienda, "cliente" + CONTADOR.incrementAndGet() + "-" + System.nanoTime() % 100000);
    }

    /** Dirección en Miraflores (Lima), elegida de la lista oficial: la que exige una venta nacional. */
    public long nuevaDireccion(long idCliente) {
        return jdbc.queryForObject("""
                INSERT INTO direcciones (id_login, id_pais, id_division, destinatario, telefono, direccion, predeterminada)
                SELECT ?, 'PE', id_division, 'Cliente de prueba', '+51900000000', 'Av. Larco 123', TRUE
                  FROM divisiones WHERE id_pais = 'PE' AND codigo = '150122'
                RETURNING id_direccion""", Long.class, idCliente);
    }

    /** Una cuenta de dueño (rol 2) SIN fila en propietarios: tiene el rol, pero no es dueño de nada. */
    public long cuentaDuenoSinPropietario() {
        return nuevaCuenta(2, null, "sinprop" + CONTADOR.incrementAndGet() + "-" + System.nanoTime() % 100000);
    }

    /**
     * Un empleado (rol 4) de la tienda con estos permisos; devuelve su id_login.
     * La tienda debe tener un plan con empleados (Básico o Pro) para que pueda entrar.
     */
    public long nuevoEmpleado(long idTienda, boolean pedidos, boolean productos, boolean inventario, boolean clientes) {
        long idLogin = nuevaCuenta(4, null, "empleado" + CONTADOR.incrementAndGet() + "-" + System.nanoTime() % 100000);
        jdbc.update("""
                INSERT INTO empleados_tienda (id_login, id_tienda, puede_pedidos, puede_productos, puede_inventario, puede_clientes)
                VALUES (?, ?, ?, ?, ?, ?)""", idLogin, idTienda, pedidos, productos, inventario, clientes);
        return idLogin;
    }

    /** El mismo empleado, ahora también en otra tienda (una persona puede trabajar en varias). */
    public void agregarEmpleado(long idLogin, long idTienda, boolean pedidos) {
        jdbc.update("""
                INSERT INTO empleados_tienda (id_login, id_tienda, puede_pedidos, puede_productos)
                VALUES (?, ?, ?, ?)""", idLogin, idTienda, pedidos, !pedidos);
    }

    public void suspenderEmpleado(long idLogin, long idTienda) {
        jdbc.update("UPDATE empleados_tienda SET estado = 0 WHERE id_login = ? AND id_tienda = ?", idLogin, idTienda);
    }

    /**
     * Un producto simple (una sola variante) con su precio y su stock. Devuelve el id de la
     * VARIANTE, que es lo que se vende.
     *
     * @param estado 0 borrador · 1 activo · 2 archivado
     */
    public long nuevoProducto(long idTienda, String precio, int stock, int estado) {
        return nuevoProducto(idTienda, precio, null, stock, estado);
    }

    /**
     * Igual, pero con el costo por unidad (V21; null = sin costo cargado). El costo se pone al crear el
     * precio porque la base no deja editarlo después (trigger T3).
     */
    public long nuevoProducto(long idTienda, String precio, String costo, int stock, int estado) {
        int n = CONTADOR.incrementAndGet();
        Long idProducto = jdbc.queryForObject("""
                INSERT INTO productos (id_tienda, nombre, slug, estado)
                VALUES (?, ?, ?, ?) RETURNING id_producto""", Long.class,
                idTienda, "Producto " + n, "producto-" + n, estado);
        Long idVariante = jdbc.queryForObject(
                "INSERT INTO variantes (id_producto) VALUES (?) RETURNING id_variante", Long.class, idProducto);
        jdbc.update("INSERT INTO precios (id_variante, monto, costo) VALUES (?, ?, ?)", idVariante,
                new BigDecimal(precio), costo == null ? null : new BigDecimal(costo));
        jdbc.update("INSERT INTO inventario (id_variante, cantidad_disponible) VALUES (?, ?)", idVariante, stock);
        return idVariante;
    }

    public long nuevoProducto(long idTienda, String precio, int stock) {
        return nuevoProducto(idTienda, precio, stock, 1);
    }

    /** Un producto activo con su costo por unidad. */
    public long nuevoProductoConCosto(long idTienda, String precio, String costo, int stock) {
        return nuevoProducto(idTienda, precio, costo, stock, 1);
    }

    public int stock(long idVariante) {
        return jdbc.queryForObject("SELECT cantidad_disponible FROM inventario WHERE id_variante = ?",
                Integer.class, idVariante);
    }

    public void cambiarPlan(long idTienda, int idPlan) {
        jdbc.update("UPDATE tiendas SET id_plan = ? WHERE id_tienda = ?", idPlan, idTienda);
    }

    // ------------------------------------------------------------------ apoyo

    /** Una cuenta del equipo de Vendely (rol 1, SUPERADMIN). */
    public long nuevoSuperadmin() {
        return nuevaCuenta(1, null, "soporte" + CONTADOR.incrementAndGet() + "-" + System.nanoTime() % 100000);
    }

    private long nuevaCuenta(int rol, Long idTienda, String usuario) {
        return jdbc.queryForObject("""
                INSERT INTO login (id_rol, id_tienda, usuario, correo, contrasena_hash, nombres, primer_apellido)
                VALUES (?, ?, ?, ?, ?, 'Prueba', 'Automatica') RETURNING id_login""", Long.class,
                rol, idTienda, usuario, usuario + "@prueba.pe", hash);
    }

    private String correoDe(long idLogin) {
        return jdbc.queryForObject("SELECT correo FROM login WHERE id_login = ?", String.class, idLogin);
    }
}
