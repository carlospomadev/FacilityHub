package com.facilityhub.service;

import com.facilityhub.domain.AuditoriaEvento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.AuditoriaEventoRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// FH-006 / P6-C1: auditoria de acciones en la misma transaccion que la accion auditada.
// El detalle solo admite valores simples (ids, estados, fechas, contadores); nunca datos personales ni contenido.
@Service
public class AuditoriaService {
    private final AuditoriaEventoRepository repository;
    private final Clock clock;

    public AuditoriaService(AuditoriaEventoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void registrar(String entidad, Long entidadId, String accion, Usuario usuario, Map<String, ?> detalle) {
        repository.save(new AuditoriaEvento(entidad, entidadId, accion, usuario, LocalDateTime.now(clock), json(detalle)));
    }

    public static Map<String, Object> detalle(Object... claveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < claveValor.length; i += 2) {
            m.put(String.valueOf(claveValor[i]), claveValor[i + 1]);
        }
        return m;
    }

    static String json(Map<String, ?> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean primero = true;
        for (Map.Entry<String, ?> e : detalle.entrySet()) {
            if (!primero) sb.append(',');
            primero = false;
            sb.append('"').append(escapar(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else if (v instanceof Iterable<?> it) {
                sb.append('[');
                boolean p = true;
                for (Object x : it) { if (!p) sb.append(','); p = false; sb.append(x instanceof Number ? x.toString() : "\"" + escapar(String.valueOf(x)) + "\""); }
                sb.append(']');
            }
            else sb.append('"').append(escapar(String.valueOf(v))).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escapar(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.toString();
    }
}
