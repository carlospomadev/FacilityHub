package com.facilityhub.security;

import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacilityHubUserDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarioRepository;

    public FacilityHubUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return User.withUsername(usuario.getEmail())
            .password(usuario.getPasswordHash())
            .roles(usuario.getRol().getCodigo())
            .disabled(!usuario.isActivo())
            .build();
    }
}
