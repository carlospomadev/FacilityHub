package com.facilityhub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class DocumentStorageService {
    private final Path root;

    public DocumentStorageService(@Value("${facilityhub.storage.root}") String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    public String guardar(Long mantenimientoId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio");
        }
        String original = file.getOriginalFilename() == null ? "documento" : file.getOriginalFilename();
        String limpio = Paths.get(original).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        String nombre = UUID.randomUUID() + "_" + limpio;
        Path carpeta = root.resolve(String.valueOf(mantenimientoId)).normalize();
        Path destino = carpeta.resolve(nombre).normalize();
        if (!destino.startsWith(root)) {
            throw new IllegalArgumentException("Ruta de archivo no valida");
        }
        try {
            Files.createDirectories(carpeta);
            Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo almacenar el documento", ex);
        }
    }

    public Resource cargar(String rutaArchivo) {
        try {
            Path ruta = Paths.get(rutaArchivo).toAbsolutePath().normalize();
            if (!ruta.startsWith(root) || !Files.exists(ruta)) {
                throw new RecursoNoEncontradoException("Archivo no encontrado");
            }
            return new UrlResource(ruta.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("No se pudo leer el archivo", ex);
        }
    }
}
