/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package curso.fullstack.shopify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EnableAsync: permite tareas en segundo plano (envio de correos).
 * EnableScheduling: permite tareas programadas (vencimiento de planes).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Shopify {
    public static void main(String[] args) {
        SpringApplication.run(Shopify.class, args);
    }
}
