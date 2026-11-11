package curso.fullstack.shopify.dto;

import java.util.List;

/** Una opción del producto con sus valores en orden ("Talla": ["S", "M", "L"]). */
public record OpcionResponse(String nombre, List<String> valores) {}
