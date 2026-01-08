package com.qualtech_ai.service;

import com.qualtech_ai.dto.SentimentResponse;
import com.qualtech_ai.dto.VideoResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.io.InputStream;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class VideoAnalysisService {

    private final S3Client s3Client;
    private final TranscribeClient transcribeClient;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final AzureBlobService azureBlobService;
    private final AzureSpeechService azureSpeechService;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${analysis.provider:aws}")
    private String provider;

    // We'll use OkHttp for fetching the transcript JSON from the Presigned URL
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(VideoAnalysisService.class);

    public VideoAnalysisService(S3Client s3Client, TranscribeClient transcribeClient,
            SentimentAnalysisService sentimentAnalysisService, AzureBlobService azureBlobService,
            AzureSpeechService azureSpeechService) {
        this.s3Client = s3Client;
        this.transcribeClient = transcribeClient;
        this.sentimentAnalysisService = sentimentAnalysisService;
        this.azureBlobService = azureBlobService;
        this.azureSpeechService = azureSpeechService;
    }

    public VideoResult analyzeVideo(MultipartFile file, String requestedProvider)
            throws IOException, InterruptedException {
        String fileName = "video-" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        // Use requested provider if present, otherwise fall back to default from config
        String activeProvider = (requestedProvider != null && !requestedProvider.isEmpty()) ? requestedProvider
                : provider;

        log.info("Starting video analysis (provider: {}) for file: {}, size: {} bytes", activeProvider, fileName,
                file.getSize());

        String transcriptText;
        if ("azure".equalsIgnoreCase(activeProvider)) {
            // Azure Pipeline
            String blobUrl = azureBlobService.uploadFile(file, fileName);
            transcriptText = azureSpeechService.transcribeFile(blobUrl);
        } else {
            // AWS Pipeline (Default)
            // 1. Upload to S3
            try {
                uploadToS3(file, fileName);
                log.info("Successfully uploaded to S3: {}", fileName);
            } catch (S3Exception e) {
                log.error("AWS S3 Upload Failed for {}: {}", fileName, e.awsErrorDetails().errorMessage(), e);
                throw new IOException("AWS S3 Error: " + e.awsErrorDetails().errorMessage(), e);
            } catch (Exception e) {
                log.error("Failed to upload to S3: {}", fileName, e);
                throw e;
            }

            // 2. Start Transcription
            String jobName = "transcription-" + UUID.randomUUID();
            log.info("Starting transcription job: {}", jobName);
            startTranscriptionJob(jobName, fileName);

            // 3. Wait for Transcription to complete (Polling)
            String transcriptUrl = waitForTranscriptionCompletion(jobName);
            log.info("Transcription completed, URL: {}", transcriptUrl);

            // 4. Download Transcript
            transcriptText = downloadTranscript(transcriptUrl);
        }

        log.debug("Transcript obtained, length: {}", transcriptText.length());

        // 5. Analyze Sentiment (SentimentAnalysisService handles provider internal
        // logic)
        SentimentResponse sentimentResponse = sentimentAnalysisService.analyzeSentiment(transcriptText, activeProvider);

        return new VideoResult(file.getOriginalFilename(), transcriptText, sentimentResponse);
    }

    private void uploadToS3(MultipartFile file, String fileName) throws IOException {
        log.info("Starting multipart upload to S3 for file: {}, size: {} bytes", fileName, file.getSize());

        // 1. Initiate Multipart Upload
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
        String uploadId = createResponse.uploadId();
        List<CompletedPart> completedParts = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[10 * 1024 * 1024]; // 10MB parts
            int bytesRead;
            int partNumber = 1;

            while ((bytesRead = is.read(buffer)) > 0) {
                log.debug("Uploading part {} for uploadId: {}", partNumber, uploadId);

                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();

                String etag = s3Client.uploadPart(uploadPartRequest,
                        RequestBody.fromBytes(Arrays.copyOf(buffer, bytesRead)))
                        .eTag();

                completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(etag).build());
                log.info("Uploaded part {} of {} for file {}", partNumber, (file.getSize() / buffer.length) + 1,
                        fileName);
                partNumber++;
            }

            // 3. Complete Multipart Upload
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();

            s3Client.completeMultipartUpload(completeRequest);
            log.info("Multipart upload completed successfully for file: {}", fileName);

        } catch (Exception e) {
            log.error("Error during multipart upload for file: {}. Aborting upload.", fileName, e);
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .uploadId(uploadId)
                    .build());
            throw new IOException("Failed to upload file to S3 via multipart", e);
        }
    }

    private void startTranscriptionJob(String jobName, String fileName) {
        String s3Uri = "s3://" + bucketName + "/" + fileName;

        // Determine media format based on file extension, defaulting to MP4
        MediaFormat mediaFormat = MediaFormat.MP4;
        if (fileName.toLowerCase().endsWith(".mov"))
            mediaFormat = MediaFormat.MP4; // AWS Transcribe doesn't have a specific MOV type, treating as MP4
        // Add other formats as needed

        try {
            StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .media(Media.builder().mediaFileUri(s3Uri).build())
                    .mediaFormat(mediaFormat)
                    .languageCode(LanguageCode.EN_US)
                    .build();

            transcribeClient.startTranscriptionJob(request);
        } catch (TranscribeException e) {
            log.error("AWS Transcribe Error for job {}: {}", jobName, e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("AWS Transcribe Error: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error starting transcribe job {}", jobName, e);
            throw e;
        }
    }

    private String waitForTranscriptionCompletion(String jobName) throws InterruptedException {
        int maxRetries = 600; // Wait up to ~20 minutes (assuming 2s sleep)

        while (maxRetries > 0) {
            GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .build();

            GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(request);
            TranscriptionJobStatus status = response.transcriptionJob().transcriptionJobStatus();

            if (status == TranscriptionJobStatus.COMPLETED) {
                return response.transcriptionJob().transcript().transcriptFileUri();
            } else if (status == TranscriptionJobStatus.FAILED) {
                throw new RuntimeException("Transcription failed: " + response.transcriptionJob().failureReason());
            }

            Thread.sleep(2000);
            maxRetries--;
        }
        throw new RuntimeException("Transcription timed out");
    }

    private String downloadTranscript(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Unexpected code " + response.statusCode());
        }

        String jsonData = response.body();
        JsonNode root = objectMapper.readTree(jsonData);

        // AWS Transcribe JSON format: results -> transcripts -> [ { "transcript": "..."
        // } ]
        // For long videos, transcripts can sometimes be split across segments or just
        // be a single large one.
        // We'll concatenate all transcript entries to be safe.
        StringBuilder fullTranscript = new StringBuilder();
        JsonNode transcripts = root.path("results").path("transcripts");
        if (transcripts.isArray()) {
            for (JsonNode node : transcripts) {
                fullTranscript.append(node.path("transcript").asText()).append(" ");
            }
        }

        return fullTranscript.toString().trim();
    }
}
