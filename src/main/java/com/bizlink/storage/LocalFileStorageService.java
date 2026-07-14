package com.bizlink.storage;

import com.bizlink.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file, String folder) {
        try {
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String filename = UUID.randomUUID() + "_" + sanitize(original);
            Path dir = Paths.get(uploadDir, folder).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            String path = "/uploads/" + folder + "/" + filename;
            log.debug("Stored file locally: {}", path);
            return path;
        } catch (IOException e) {
            log.error("Local file upload failed", e);
            throw new ValidationException("Failed to upload file");
        }
    }

    @Override
    public String storeBytes(byte[] data, String folder, String filename, String contentType) {
        try {
            String safeName = sanitize(filename);
            Path dir = Paths.get(uploadDir, folder).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName);
            Files.write(target, data);
            String path = "/uploads/" + folder + "/" + safeName;
            log.debug("Stored bytes locally: {}", path);
            return path;
        } catch (IOException e) {
            log.error("Local byte storage failed", e);
            throw new ValidationException("Failed to store file");
        }
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
