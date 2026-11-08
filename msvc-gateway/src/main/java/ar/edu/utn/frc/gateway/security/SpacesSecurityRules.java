package ar.edu.utn.frc.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;

@Component
public class SpacesSecurityRules {

    public void configure(
            ServerHttpSecurity.AuthorizeExchangeSpec exchanges) {

        // ---------------------------------------------------------
        // Operaciones exclusivas de servicios internos.
        // No se permite acceder a ellas por el Gateway público.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.POST,
                "/api/spaces/ocupaciones/retenciones",
                "/api/spaces/ocupaciones/retenciones/",
                "/api/spaces/ocupaciones/{id}/traslado",
                "/api/spaces/ocupaciones/{id}/traslado/",
                "/api/spaces/ocupaciones/vencimientos",
                "/api/spaces/ocupaciones/vencimientos/")
                .denyAll();

        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/spaces/ocupaciones/{id}/confirmacion",
                "/api/spaces/ocupaciones/{id}/confirmacion/",
                "/api/spaces/ocupaciones/{id}/liberacion",
                "/api/spaces/ocupaciones/{id}/liberacion/")
                .denyAll();

        // ---------------------------------------------------------
        // Deportes.
        // ---------------------------------------------------------

        // Consultar el catálogo de deportes.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/deportes",
                "/api/spaces/deportes/")
                .permitAll();

        // Registrar un deporte.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/spaces/deportes",
                "/api/spaces/deportes/")
                .hasRole("ADMIN");

        // Actualizar un deporte.
        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/spaces/deportes/{id}",
                "/api/spaces/deportes/{id}/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Complejos.
        // ---------------------------------------------------------

        // Buscar complejos y consultar su detalle.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/complejos",
                "/api/spaces/complejos/",
                "/api/spaces/complejos/{id}",
                "/api/spaces/complejos/{id}/")
                .permitAll();

        // Registrar un complejo.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/spaces/complejos",
                "/api/spaces/complejos/")
                .hasRole("ADMIN");

        // Actualizar un complejo.
        // Spaces debe validar la asignación del administrador.
        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/spaces/complejos/{id}",
                "/api/spaces/complejos/{id}/")
                .hasRole("ADMIN");

        // Activar o desactivar un complejo.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/spaces/complejos/{id}/estado",
                "/api/spaces/complejos/{id}/estado/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Configuración de reservas.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/complejos/{id}/configuracion-reservas",
                "/api/spaces/complejos/{id}/configuracion-reservas/")
                .authenticated();

        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/spaces/complejos/{id}/configuracion-reservas",
                "/api/spaces/complejos/{id}/configuracion-reservas/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Horarios.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/complejos/{id}/horarios",
                "/api/spaces/complejos/{id}/horarios/")
                .authenticated();

        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/spaces/complejos/{id}/horarios",
                "/api/spaces/complejos/{id}/horarios/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Canchas.
        // ---------------------------------------------------------

        // Registrar una cancha.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/spaces/complejos/{id}/canchas",
                "/api/spaces/complejos/{id}/canchas/")
                .hasRole("ADMIN");

        // Listar canchas y consultar una cancha.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/complejos/{id}/canchas",
                "/api/spaces/complejos/{id}/canchas/",
                "/api/spaces/canchas/{id}",
                "/api/spaces/canchas/{id}/")
                .authenticated();

        // Actualizar una cancha.
        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/spaces/canchas/{id}",
                "/api/spaces/canchas/{id}/")
                .hasRole("ADMIN");

        // Activar o desactivar una cancha.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/spaces/canchas/{id}/estado",
                "/api/spaces/canchas/{id}/estado/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Tarifas.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/canchas/{id}/tarifa",
                "/api/spaces/canchas/{id}/tarifa/")
                .authenticated();

        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/spaces/canchas/{id}/tarifa",
                "/api/spaces/canchas/{id}/tarifa/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Disponibilidad.
        // ---------------------------------------------------------

        // Consulta pública de disponibilidad de un complejo.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/complejos/{id}/disponibilidad",
                "/api/spaces/complejos/{id}/disponibilidad/")
                .permitAll();

        // Consulta autenticada de disponibilidad de una cancha.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/canchas/{id}/disponibilidad",
                "/api/spaces/canchas/{id}/disponibilidad/")
                .authenticated();

        // ---------------------------------------------------------
        // Ocupaciones.
        // ---------------------------------------------------------

        // Consultar la agenda ocupada de una cancha.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/canchas/{id}/ocupaciones",
                "/api/spaces/canchas/{id}/ocupaciones/")
                .hasRole("ADMIN");

        // Consultar una ocupación.
        // El acceso de servicios internos se configura aparte.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/ocupaciones/{id}",
                "/api/spaces/ocupaciones/{id}/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Bloqueos.
        // ---------------------------------------------------------

        // Bloquear un período de una cancha.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/spaces/canchas/{id}/bloqueos",
                "/api/spaces/canchas/{id}/bloqueos/")
                .hasRole("ADMIN");

        // Listar los bloqueos de una cancha.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/spaces/canchas/{id}/bloqueos",
                "/api/spaces/canchas/{id}/bloqueos/")
                .hasRole("ADMIN");

        // Levantar un bloqueo.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/spaces/bloqueos/{id}/liberacion",
                "/api/spaces/bloqueos/{id}/liberacion/")
                .hasRole("ADMIN");
    }
}