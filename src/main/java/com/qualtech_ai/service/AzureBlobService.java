package com.qualtech_ai.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.qualtech_ai.exception.AzureServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class AzureBlobService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobService.class);
    private final BlobServiceClient blobServiceClient;

    @Value("${azure.storage.container-name:qualtech-container}")
    private String containerName;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public AzureBlobService(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    public AzureBlobService() {
        this.blobServiceClient = null;
    }

    /**
     * Uploads a file to Azure Blob Storage
     * 
     * @param file     The file to upload
     * @param fileName The blob name
     * @return The blob URL (without SAS token)
     * @throws IOException If upload fails
     */
    public String uploadFile(MultipartFile file, String fileName) throws IOException {
        if (blobServiceClient == null) {
            throw new AzureServiceException("Blob Storage", "upload", "Azure Blob Storage is not configured.");
        }

        if (file == null || file.isEmpty()) {
            throw new AzureServiceException("Blob Storage", "upload", "File is null or empty.");
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new AzureServiceException("Blob Storage", "upload", "File name is null or empty.");
        }

        log.info("Uploading file to Azure Blob Storage: {} (size: {} bytes)", fileName, file.getSize());

        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

            if (!containerClient.exists()) {
                log.info("Container '{}' does not exist. Creating...", containerName);
                containerClient.create();
                log.info("Container '{}' created successfully", containerName);
            }

            BlobClient blobClient = containerClient.getBlobClient(fileName);
            blobClient.upload(file.getInputStream(), file.getSize(), true);

            log.info("File uploaded successfully to Azure Blob Storage: {}", fileName);
            return blobClient.getBlobUrl();
        } catch (Exception e) {
            log.error("Failed to upload file to Azure Blob Storage: {}", fileName, e);
            throw new AzureServiceException("Blob Storage", "upload", "Upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a SAS (Shared Access Signature) URL for secure, time-limited access
     * to a blob
     * This is required for Azure Speech Service to access private blobs
     * 
     * @param blobName The name of the blob
     * @param validity The duration for which the SAS token is valid
     * @return A URL with SAS token appended
     */
    public String generateSasUrl(String blobName, Duration validity) {
        if (blobServiceClient == null) {
            throw new AzureServiceException("Blob Storage", "generateSasUrl", "Azure Blob Storage is not configured.");
        }

        if (blobName == null || blobName.trim().isEmpty()) {
            throw new AzureServiceException("Blob Storage", "generateSasUrl", "Blob name is null or empty.");
        }

        if (validity == null || validity.isNegative() || validity.isZero()) {
            throw new AzureServiceException("Blob Storage", "generateSasUrl", "Invalid validity duration provided.");
        }

        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            // Create SAS token with READ permission
            BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
            OffsetDateTime expiryTime = OffsetDateTime.now().plus(validity);

            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);
            String sasToken = blobClient.generateSas(sasValues);

            String sasUrl = blobClient.getBlobUrl() + "?" + sasToken;
            log.info("Generated SAS URL for blob '{}' (valid for {} hours)", blobName, validity.toHours());

            return sasUrl;
        } catch (Exception e) {
            log.error("Failed to generate SAS URL for blob: {}", blobName, e);
            throw new AzureServiceException("Blob Storage", "generateSasUrl", "SAS URL generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the plain blob URL (without SAS token)
     * 
     * @param blobName The name of the blob
     * @return The blob URL
     */
    public String getBlobUrl(String blobName) {
        if (blobServiceClient == null) {
            throw new AzureServiceException("Blob Storage", "getBlobUrl", "Azure Blob Storage is not configured.");
        }

        if (blobName == null || blobName.trim().isEmpty()) {
            throw new AzureServiceException("Blob Storage", "getBlobUrl", "Blob name is null or empty.");
        }

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        return blobClient.getBlobUrl();
    }

    /**
     * Gets the blob URL with a SAS token (default validity: 12 hours)
     * 
     * @param blobName The name of the blob
     * @return The blob URL with SAS token
     */
    public String getBlobUrlWithSas(String blobName) {
        return generateSasUrl(blobName, Duration.ofHours(12));
    }

    /**
     * Deletes a blob if it exists
     * 
     * @param blobName The name of the blob to delete
     */
    public void deleteBlobIfExists(String blobName) {
        if (blobServiceClient == null) {
            throw new AzureServiceException("Blob Storage", "delete", "Azure Blob Storage is not configured.");
        }

        if (blobName == null || blobName.trim().isEmpty()) {
            throw new AzureServiceException("Blob Storage", "delete", "Blob name is null or empty.");
        }

        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            if (blobClient.exists()) {
                blobClient.delete();
                log.info("Deleted blob: {}", blobName);
            } else {
                log.debug("Blob does not exist, skipping deletion: {}", blobName);
            }
        } catch (Exception e) {
            log.error("Failed to delete blob: {}", blobName, e);
            throw new AzureServiceException("Blob Storage", "delete", "Blob deletion failed: " + e.getMessage(), e);
        }
    }
}
