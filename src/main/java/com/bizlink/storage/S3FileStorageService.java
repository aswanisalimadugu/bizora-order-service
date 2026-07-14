package com.bizlink.storage;

import com.bizlink.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.region}")
    private String region;

    @Value("${app.storage.s3.public-base-url:}")
    private String publicBaseUrl;

    public S3FileStorageService(@Value("${app.storage.s3.region}") String region) {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @PostConstruct
    void validateConfig() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("S3_BUCKET must be set when STORAGE_TYPE=s3");
        }
    }

    @Override
    public String store(MultipartFile file, String folder) {
        try {
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String key = folder + "/" + UUID.randomUUID() + "_" + sanitize(original);
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));

            String url = publicUrl(key);
            log.info("Stored file on S3: {}", url);
            return url;
        } catch (IOException e) {
            log.error("S3 upload failed", e);
            throw new ValidationException("Failed to upload file to storage");
        }
    }

    @Override
    public String storeBytes(byte[] data, String folder, String filename, String contentType) {
        String key = folder + "/" + sanitize(filename);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .build(),
                RequestBody.fromBytes(data));

        String url = publicUrl(key);
        log.info("Stored bytes on S3: {}", url);
        return url;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    private String publicUrl(String key) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String base = publicBaseUrl.endsWith("/")
                    ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                    : publicBaseUrl;
            return base + "/" + key;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
