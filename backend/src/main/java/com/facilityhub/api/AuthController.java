package com.facilityhub.api;

import com.facilityhub.api.dto.auth.LoginRequest;
import com.facilityhub.api.dto.auth.LoginResponse;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.UsuarioRepository;
import com.facilityhub.security.JwtTokenService;
import com.facilityhub.service.RecursoNoEncontradoException;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthenticationManager authenticationManager,
                          UsuarioRepository usuarioRepository,
                          JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
        );

        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(request.email())
            .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        String token = jwtTokenService.generarToken(usuario);
        return new LoginResponse(
            token,
            "Bearer",
            usuario.getId(),
            usuario.getNombre(),
            usuario.getEmail(),
            usuario.getRol().getCodigo()
        );
    }
}
