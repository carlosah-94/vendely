package curso.fullstack.shopify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.security.JwtService;
import curso.fullstack.shopify.service.CloudinaryClient;
import curso.fullstack.shopify.service.MercadoPagoClient;
import curso.fullstack.shopify.service.PayPalClient;
import curso.fullstack.shopify.service.ResendClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integracion (issue #9): Spring completo + un Postgres de verdad.
 *
 * Por que Postgres y no H2: el esquema usa btree_gist, EXCLUDE, triggers y columnas generadas
 * que H2 no soporta, y Hibernate arranca con ddl-auto: validate. Testcontainers levanta un
 * Postgres 16 en Docker y Flyway le aplica las mismas migraciones que al servidor.
 *
 * El contenedor es UNO para toda la suite (campo estatico que se arranca una vez): levantarlo por
 * cada clase triplicaria el tiempo. Al terminar lo borra Testcontainers solo (contenedor Ryuk).
 * Como la base es compartida, cada prueba crea sus propias tiendas con {@link DatosPrueba} y no
 * depende de lo que hayan dejado las demas.
 *
 * Todas las clases heredan los mismos @MockBean: asi Spring reutiliza un solo contexto para
 * toda la suite en vez de arrancar uno por clase.
 *
 * Requisito: Docker encendido (Docker Desktop en Windows; el runner de GitHub Actions ya lo trae).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DatosPrueba.class)
public abstract class PruebaIntegracion {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vendely_test")
            .withUsername("vendely")
            .withPassword("vendely");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // Ninguna prueba sale a internet: las integraciones externas se simulan.
    @MockBean protected MercadoPagoClient mercadoPago;
    @MockBean protected PayPalClient payPal;
    @MockBean protected ResendClient resend;
    @MockBean protected CloudinaryClient cloudinary;

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected DatosPrueba datos;
    @Autowired protected JwtService jwtService;
    @Autowired protected LoginRepository loginRepository;
    @Autowired protected ObjectMapper objectMapper;

    /** Un token valido, igual al que entrega /api/auth/login, para la cuenta indicada. */
    protected String token(long idLogin) {
        return "Bearer " + jwtService.generarToken(loginRepository.findById(idLogin).orElseThrow());
    }

    /**
     * Registra una venta desde el panel (POST /api/panel/tiendas/{id}/pedidos) para el cliente
     * de la tienda y devuelve el id del pedido. Cada par es (idVariante, cantidad).
     */
    protected long crearPedido(DatosPrueba.Tienda t, long... variantesYCantidades) throws Exception {
        return objectMapper.readTree(
                mvc.perform(post("/api/panel/tiendas/" + t.idTienda() + "/pedidos")
                                .header("Authorization", token(t.idDueno()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ventaJson(t, variantesYCantidades)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("idPedido").asLong();
    }

    /** El JSON de una venta del panel para el cliente de la tienda. */
    protected String ventaJson(DatosPrueba.Tienda t, long... variantesYCantidades) throws Exception {
        List<Map<String, Long>> lineas = new ArrayList<>();
        for (int i = 0; i < variantesYCantidades.length; i += 2) {
            lineas.add(Map.of("idVariante", variantesYCantidades[i], "cantidad", variantesYCantidades[i + 1]));
        }
        return objectMapper.writeValueAsString(Map.of(
                "idLogin", t.idCliente(), "idDireccionEnvio", t.idDireccion(), "lineas", lineas));
    }

    /** El total que la base calculó para el pedido (productos + envío). */
    protected BigDecimal totalDe(long idPedido) {
        return jdbc.queryForObject("SELECT total FROM pedidos WHERE id_pedido = ?", BigDecimal.class, idPedido);
    }
}
