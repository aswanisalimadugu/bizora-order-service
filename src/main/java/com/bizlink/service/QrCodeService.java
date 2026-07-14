package com.bizlink.service;

import com.bizlink.exception.ValidationException;
import com.bizlink.model.Business;
import com.bizlink.model.BusinessQr;
import com.bizlink.repository.BusinessQrRepository;
import com.bizlink.storage.FileStorageService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
public class QrCodeService {

    private static final int QR_SIZE = 600;

    private final BusinessQrRepository businessQrRepository;
    private final FileStorageService fileStorage;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public QrCodeService(BusinessQrRepository businessQrRepository, FileStorageService fileStorage) {
        this.businessQrRepository = businessQrRepository;
        this.fileStorage = fileStorage;
    }

    @Transactional
    public BusinessQr getOrCreate(Business business) {
        return businessQrRepository.findFirstByBusinessIdAndLabelIsNull(business.getId())
                .map(existing -> refreshIfUrlChanged(existing, business))
                .orElseGet(() -> generate(business, null));
    }

    @Transactional
    public BusinessQr regenerate(Business business) {
        BusinessQr qr = businessQrRepository.findFirstByBusinessIdAndLabelIsNull(business.getId())
                .orElse(null);
        String scanUrl = buildScanUrl(business.getSlug());
        String path = writeQrImage(business.getId(), scanUrl);
        if (qr == null) {
            qr = new BusinessQr();
            qr.setBusinessId(business.getId());
        }
        qr.setScanUrl(scanUrl);
        qr.setQrImagePath(path);
        return businessQrRepository.save(qr);
    }

    private BusinessQr refreshIfUrlChanged(BusinessQr existing, Business business) {
        String scanUrl = buildScanUrl(business.getSlug());
        if (!scanUrl.equals(existing.getScanUrl())) {
            String path = writeQrImage(business.getId(), scanUrl);
            existing.setScanUrl(scanUrl);
            existing.setQrImagePath(path);
            return businessQrRepository.save(existing);
        }
        return existing;
    }

    private BusinessQr generate(Business business, String label) {
        String scanUrl = buildScanUrl(business.getSlug());
        String path = writeQrImage(business.getId(), scanUrl);
        BusinessQr qr = new BusinessQr();
        qr.setBusinessId(business.getId());
        qr.setLabel(label);
        qr.setScanUrl(scanUrl);
        qr.setQrImagePath(path);
        return businessQrRepository.save(qr);
    }

    private String buildScanUrl(String slug) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/business/" + slug;
    }

    private String writeQrImage(java.util.UUID businessId, String content) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new MultiFormatWriter()
                    .encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            String filename = businessId + ".png";

            log.info("Generated QR for business {} -> {}", businessId, content);
            return fileStorage.storeBytes(out.toByteArray(), "qr", filename, "image/png");
        } catch (Exception e) {
            log.error("QR generation failed for business {}", businessId, e);
            throw new ValidationException("Failed to generate QR code");
        }
    }
}
