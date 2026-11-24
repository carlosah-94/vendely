package curso.fullstack.shopify.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/public/salud: el "chequeo de salud" que Render visita cada pocos segundos para saber si
 * el backend está vivo (Settings, Health Check Path).
 *
 * Responde siempre lo mismo, sin ir a la base ni depender de ninguna tienda. Antes Render visitaba
 * la tienda demo: al suspenderla respondió 404, Render creyó que el servidor estaba caído y dejó de
 * mandarle visitas. Y /api/public/paises pesa 35 KB: Render corta la conexión antes de recibirla
 * toda y llena el registro de errores. Esta respuesta pesa unos pocos bytes.
 *
 * Es como el pulso de una persona: dice si está viva, no si tuvo un buen día.
 */
@RestController
@RequestMapping("/api/public/salud")
public class SaludController {

    @GetMapping
    public Map<String, String> salud() {
        return Map.of("estado", "ok");
    }
}
