package ar.edu.utn.frc.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;

@Component
public class BookingsSecurityRules {

    public void configure(
            ServerHttpSecurity.AuthorizeExchangeSpec exchanges) {

        // ---------------------------------------------------------
        // Cotizaciones y solicitud de reservas.
        // Bookings valida el complejo asignado al administrador.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.POST,
                "/api/bookings/reservas/cotizaciones",
                "/api/bookings/reservas/cotizaciones/",
                "/api/bookings/reservas",
                "/api/bookings/reservas/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // ---------------------------------------------------------
        // Consultas con rutas específicas.
        // Se declaran antes de /reservas/{id}.
        // ---------------------------------------------------------

        // Reservas propias del cliente autenticado.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/reservas/mias",
                "/api/bookings/reservas/mias/")
                .hasRole("CLIENTE");

        // Avisos propios del cliente autenticado.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/reservas/avisos",
                "/api/bookings/reservas/avisos/")
                .hasRole("CLIENTE");

        // Reservas del complejo y reservas afectadas.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/reservas",
                "/api/bookings/reservas/",
                "/api/bookings/reservas/afectadas",
                "/api/bookings/reservas/afectadas/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Detalle e historial.
        // Bookings valida propietario o administrador del complejo.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/reservas/{id}",
                "/api/bookings/reservas/{id}/",
                "/api/bookings/reservas/{id}/historial",
                "/api/bookings/reservas/{id}/historial/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // ---------------------------------------------------------
        // Cambios de estado de reservas.
        // ---------------------------------------------------------

        // Confirmar seña, registrar realización o ausencia.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/bookings/reservas/{id}/confirmacion",
                "/api/bookings/reservas/{id}/confirmacion/",
                "/api/bookings/reservas/{id}/realizacion",
                "/api/bookings/reservas/{id}/realizacion/",
                "/api/bookings/reservas/{id}/ausencia",
                "/api/bookings/reservas/{id}/ausencia/")
                .hasRole("ADMIN");

        // Cancelar una reserva propia o del complejo administrado.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/bookings/reservas/{id}/cancelacion",
                "/api/bookings/reservas/{id}/cancelacion/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // Procesar reservas pendientes vencidas.
        // La ejecución automática del sistema se configura aparte.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/bookings/reservas/vencimientos",
                "/api/bookings/reservas/vencimientos/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Políticas climáticas.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/bookings/complejos/{complejoId}/politicas-climaticas/{deporteId}",
                "/api/bookings/complejos/{complejoId}/politicas-climaticas/{deporteId}/")
                .hasRole("ADMIN");

        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/complejos/{complejoId}/politicas-climaticas/{deporteId}",
                "/api/bookings/complejos/{complejoId}/politicas-climaticas/{deporteId}/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Evaluaciones climáticas.
        // ---------------------------------------------------------

        // Evaluación general o reintento de una reserva.
        // La ejecución automática del sistema se configura aparte.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/bookings/evaluaciones-climaticas",
                "/api/bookings/evaluaciones-climaticas/",
                "/api/bookings/reservas/{id}/evaluaciones-climaticas",
                "/api/bookings/reservas/{id}/evaluaciones-climaticas/")
                .hasRole("ADMIN");

        // Consultar evaluaciones de una reserva.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/reservas/{id}/evaluaciones-climaticas",
                "/api/bookings/reservas/{id}/evaluaciones-climaticas/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // ---------------------------------------------------------
        // Alternativas de reprogramación.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/reservas/{id}/alternativas",
                "/api/bookings/reservas/{id}/alternativas/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Propuestas de reprogramación.
        // ---------------------------------------------------------

        // Crear una propuesta o contrapropuesta.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/bookings/reservas/{id}/propuestas-reprogramacion",
                "/api/bookings/reservas/{id}/propuestas-reprogramacion/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // Consultar propuestas de una reserva o pendientes de respuesta.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/reservas/{id}/propuestas-reprogramacion",
                "/api/bookings/reservas/{id}/propuestas-reprogramacion/",
                "/api/bookings/propuestas-reprogramacion",
                "/api/bookings/propuestas-reprogramacion/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // Bookings debe comprobar que quien acepta o rechaza
        // sea el destinatario de la propuesta.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/bookings/propuestas-reprogramacion/{id}/aceptacion",
                "/api/bookings/propuestas-reprogramacion/{id}/aceptacion/",
                "/api/bookings/propuestas-reprogramacion/{id}/rechazo",
                "/api/bookings/propuestas-reprogramacion/{id}/rechazo/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // Procesar propuestas vencidas.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/bookings/propuestas-reprogramacion/vencimientos",
                "/api/bookings/propuestas-reprogramacion/vencimientos/")
                .hasRole("ADMIN");

        // ---------------------------------------------------------
        // Saldos a favor.
        // Bookings valida propietario o administrador del complejo.
        // ---------------------------------------------------------

        exchanges.pathMatchers(HttpMethod.GET,
                "/api/bookings/saldos-favor",
                "/api/bookings/saldos-favor/",
                "/api/bookings/saldos-favor/{id}",
                "/api/bookings/saldos-favor/{id}/")
                .hasAnyRole("CLIENTE", "ADMIN");

        // Solicitar revisión de un saldo propio.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/bookings/saldos-favor/{id}/revision",
                "/api/bookings/saldos-favor/{id}/revision/")
                .hasRole("CLIENTE");

        // Resolver un saldo en revisión.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/bookings/saldos-favor/{id}/resolucion",
                "/api/bookings/saldos-favor/{id}/resolucion/")
                .hasRole("ADMIN");

        // Procesar vencimientos de saldos.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/bookings/saldos-favor/vencimientos",
                "/api/bookings/saldos-favor/vencimientos/")
                .hasRole("ADMIN");
    }
}