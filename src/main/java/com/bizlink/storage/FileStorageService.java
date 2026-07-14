package com.bizlink.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded files locally or on S3. Returns a URL/path stored in the database.
 */
public interface FileStorageService {

    String store(MultipartFile file, String folder);

    String storeBytes(byte[] data, String folder, String filename, String contentType);

    boolean isLocal();
}
