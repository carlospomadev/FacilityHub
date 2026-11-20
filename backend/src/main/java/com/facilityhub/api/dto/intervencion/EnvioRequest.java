package com.facilityhub.api.dto.intervencion;

import jakarta.validation.constraints.NotNull;

// La version leida por el cliente: si otro usuario modifico la intervencion, el envio responde 409.
public record EnvioRequest(@NotNull Long version) {}
