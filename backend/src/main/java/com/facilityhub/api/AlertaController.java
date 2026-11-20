package com.facilityhub.api;

import com.facilityhub.api.dto.alerta.AlertaDto;
import com.facilityhub.service.AlertaService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

// FH-006 / P6-D1: alertas propias de cualquier usuario autenticado (el destinatario se toma siempre de la sesion).
@RestController
@RequestMapping("/api/alertas")
public class AlertaController {
    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    public List<AlertaDto> listar(@RequestParam(defaultValue = "false") boolean soloNoLeidas, Principal principal) {
        return alertaService.propias(email(principal), soloNoLeidas);
    }

    @GetMapping("/no-leidas")
    public Map<String, Long> noLeidas(Principal principal) {
        return Map.of("noLeidas", alertaService.noLeidas(email(principal)));
    }

    @PatchMapping("/{id}/leida")
    public AlertaDto marcarLeida(@PathVariable Long id, Principal principal) {
        return alertaService.marcarLeida(id, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
