package curso.fullstack.shopify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** El chequeo de salud de Render: responde sin sesión y sin depender de ninguna tienda. */
class SaludTest extends PruebaIntegracion {

    @Test
    void respondeOkSinIniciarSesion() throws Exception {
        mvc.perform(get("/api/public/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"));
    }
}
