package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V24: RUC y razón social del dueño. Para vender en Perú hace falta RUC; el RUC se valida con su
 * dígito verificador (SUNAT) en el servicio y en la base, y un RUC 10 debe llevar el DNI del dueño.
 */
class RucDuenoTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;
    private String dni;

    /** Un dueño que se registró con su DNI y todavía no tiene RUC. */
    @BeforeEach
    void preparar() {
        tienda = datos.nuevaTienda();
        dni = String.format("%08d", (System.nanoTime() / 1000) % 100_000_000L);
        jdbc.update("""
                UPDATE propietarios SET tipo_documento = 1, numero_documento = ?, ruc = NULL, razon_social = NULL
                 WHERE id_login = ?""", dni, tienda.idDueno());
        jdbc.update("UPDATE login SET nombres = 'Rosa', primer_apellido = 'Quispe', segundo_apellido = NULL WHERE id_login = ?",
                tienda.idDueno());
    }

    @Test
    void sinRucLaTiendaNoPuedeVender() throws Exception {
        mvc.perform(get("/api/public/tiendas/" + tienda.subdominio()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.puedeVender").value(false));
        perfil().andExpect(jsonPath("$.negocio.listoParaVender").value(false));
        // Aunque alguien se salte la página, la base no deja crear el pedido.
        long variante = datos.nuevoProducto(tienda.idTienda(), "50.00", 5);
        mvc.perform(post("/api/panel/tiendas/" + tienda.idTienda() + "/pedidos").header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content(ventaJson(tienda, variante, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("RUC")));
    }

    @Test
    void conSuRuc10YaVendeYLaRazonSocialEsSuNombre() throws Exception {
        String ruc = conDigito("10" + dni);
        guardar(ruc, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.negocio.ruc").value(ruc))
                .andExpect(jsonPath("$.perfil.negocio.razonSocial").value("Rosa Quispe"))
                .andExpect(jsonPath("$.perfil.negocio.listoParaVender").value(true));
        mvc.perform(get("/api/public/tiendas/" + tienda.subdominio())).andExpect(jsonPath("$.puedeVender").value(true));
        long variante = datos.nuevoProducto(tienda.idTienda(), "50.00", 5);
        crearPedido(tienda, variante, 1);
    }

    @Test
    void unRucConElDigitoEquivocadoSeRechaza() throws Exception {
        String ruc = conDigito("10" + dni);
        String malo = ruc.substring(0, 10) + ((ruc.charAt(10) - '0' + 1) % 10);
        guardar(malo, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no es válido")));
        guardar("2010006660", "Empresa").andExpect(status().isBadRequest());   // 10 dígitos
        guardar("30100066603", "Empresa").andExpect(status().isBadRequest());  // prefijo que no existe
    }

    @Test
    void unRuc10DebeLlevarSuDni() throws Exception {
        String otroDni = dni.equals("12345678") ? "87654321" : "12345678";
        guardar(conDigito("10" + otroDni), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString(dni)));
    }

    @Test
    void unRuc20PideSuRazonSocial() throws Exception {
        String ruc = DatosPrueba.nuevoRuc("20");
        guardar(ruc, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("razón social")));
        guardar(ruc, "Dulces Rosa S.A.C.").andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.negocio.razonSocial").value("Dulces Rosa S.A.C."));
    }

    @Test
    void unRucDeOtraCuentaNoSeRepite() throws Exception {
        String rucAjeno = jdbc.queryForObject("SELECT ruc FROM propietarios WHERE id_login = ?", String.class,
                datos.nuevaTienda().idDueno());
        guardar(rucAjeno, "Copia S.A.C.").andExpect(status().isConflict());
    }

    @Test
    void laBaseTambienRevisaElVerificador() {
        assertThat(jdbc.queryForObject("SELECT fn_ruc_valido('20100066603')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT fn_ruc_valido('20100066604')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT fn_rut_valido('12345678-5')", Boolean.class)).isTrue();
        // Un RUC mal escrito no entra ni escribiendo directo en la base.
        assertThrows(Exception.class, () -> jdbc.update("UPDATE propietarios SET ruc = '20100066604', razon_social = 'X S.A.C.' "
                + "WHERE id_login = ?", tienda.idDueno()));
        // Ni un RUC 10 que no lleva su DNI, ni un RUC sin razón social.
        String otroDni = dni.equals("12345678") ? "87654321" : "12345678";
        assertThrows(Exception.class, () -> jdbc.update("UPDATE propietarios SET ruc = ?, razon_social = 'Rosa' WHERE id_login = ?",
                conDigito("10" + otroDni), tienda.idDueno()));
        assertThrows(Exception.class, () -> jdbc.update("UPDATE propietarios SET ruc = ?, razon_social = NULL WHERE id_login = ?",
                DatosPrueba.nuevoRuc("20"), tienda.idDueno()));
    }

    @Test
    void elVerificadorEnJava() {
        assertThat(DocumentoTributario.esRucValido("20100066603")).isTrue();
        assertThat(DocumentoTributario.esRucValido("20100066604")).isFalse();
        assertThat(DocumentoTributario.esRutValido("12345678-5")).isTrue();
        assertThat(DocumentoTributario.esRutValido("12345678-4")).isFalse();
        assertThat(DocumentoTributario.esRutValido("11111111-1")).isTrue();
    }

    // ------------------------------------------------------------------ apoyo

    private static String conDigito(String diez) {
        return diez + DocumentoTributario.digitoRuc(diez);
    }

    private ResultActions perfil() throws Exception {
        return mvc.perform(get("/api/cuenta/perfil").header("Authorization", token(tienda.idDueno())));
    }

    private ResultActions guardar(String ruc, String razonSocial) throws Exception {
        return mvc.perform(put("/api/cuenta/perfil").header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombres\":\"Rosa\",\"primerApellido\":\"Quispe\",\"ruc\":\"" + ruc
                        + "\",\"razonSocial\":\"" + razonSocial + "\"}"));
    }
}
