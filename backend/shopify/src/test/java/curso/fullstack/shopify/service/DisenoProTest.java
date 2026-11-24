package curso.fullstack.shopify.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Diseño avanzado de la vitrina (plan Pro): portada a medida, anuncio con varios mensajes,
 * promoción, categorías en el menú y etiqueta de descuento.
 */
class DisenoProTest extends PruebaIntegracion {

    private static final String DISENO = """
            "disenoPro":{
              "portada":{"activa":true,"titulo":"  Black Friday  ","subtitulo":"30 % en toda la tienda",
                         "boton1Texto":"Comprar","boton1Enlace":"#catalogo","alineacion":"centro","oscurecer":50,"alto":"pantalla"},
              "anuncio":{"color":"#f4b6c2","mensajes":["Envío gratis desde S/ 99","", "Cambios en 30 días"]},
              "promo":{"mostrar":true,"titulo":"50 % en packs","botonTexto":"Ver packs","botonEnlace":"/tienda/x?categoria=packs","color":"#0b4d3a"},
              "categoriasEnMenu":true,
              "mostrarDescuento":true
            }""";

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
    }

    @Test
    void unaTiendaProGuardaSuDisenoYLaVitrinaLoMuestraLimpio() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 3);

        actualizar(DISENO).andExpect(status().isOk())
                .andExpect(jsonPath("$.diseno.pro.portada.titulo").value("Black Friday"));

        mvc.perform(get("/api/public/tiendas/" + tienda.subdominio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diseno.pro.portada.activa").value(true))
                .andExpect(jsonPath("$.diseno.pro.portada.alto").value("pantalla"))
                .andExpect(jsonPath("$.diseno.pro.anuncio.color").value("#F4B6C2"))
                .andExpect(jsonPath("$.diseno.pro.anuncio.mensajes.length()").value(2))   // el vacío se quita
                .andExpect(jsonPath("$.diseno.pro.promo.mostrar").value(true))
                .andExpect(jsonPath("$.diseno.pro.categoriasEnMenu").value(true));
    }

    @Test
    void conPlanBasicoNoSePuedeUsarPeroSiVolverALoDeSiempre() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 2);

        actualizar(DISENO).andExpect(status().isForbidden());
        // Todo apagado = la portada y el anuncio de siempre: lo puede guardar cualquier plan.
        actualizar("\"disenoPro\":{\"portada\":{\"activa\":false},\"promo\":{\"mostrar\":false}}").andExpect(status().isOk());
    }

    @Test
    void siLaTiendaBajaDePlanLaVitrinaVuelveALoDeSiempre() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 3);
        actualizar(DISENO).andExpect(status().isOk());
        datos.cambiarPlan(tienda.idTienda(), 1);

        mvc.perform(get("/api/public/tiendas/" + tienda.subdominio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diseno.pro.portada.activa").value(false))
                .andExpect(jsonPath("$.diseno.pro.promo.mostrar").value(false));
    }

    @Test
    void unEnlaceJavascriptOUnColorMalEscritoSonError() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 3);
        actualizar("\"disenoPro\":{\"portada\":{\"activa\":true,\"boton1Enlace\":\"javascript:alert(1)\"}}")
                .andExpect(status().isBadRequest());
        actualizar("\"disenoPro\":{\"promo\":{\"mostrar\":true,\"color\":\"verde\"}}")
                .andExpect(status().isBadRequest());
        actualizar("\"disenoPro\":{\"anuncio\":{\"mensajes\":[\"a\",\"b\",\"c\",\"d\"]}}")
                .andExpect(status().isBadRequest());
    }

    private ResultActions actualizar(String campoExtra) throws Exception {
        return mvc.perform(put("/api/panel/tiendas/" + tienda.idTienda())
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Mi tienda\",\"correoContacto\":\"ventas@prueba.pe\"," + campoExtra + "}"));
    }
}
