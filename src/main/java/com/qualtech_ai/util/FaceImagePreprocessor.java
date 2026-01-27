package com.qualtech_ai.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.springframework.stereotype.Component;

import org.bytedeco.javacpp.BytePointer;

@Slf4j
@Component
public class FaceImagePreprocessor {

    private static final int TARGET_FACE_SIZE = 224;
    private static final double BRIGHTNESS_THRESHOLD_LOW = 80.0;
    private static final double BRIGHTNESS_THRESHOLD_HIGH = 180.0;

    public Mat preprocessFaceImage(Mat faceRoi) {
        if (faceRoi == null || faceRoi.empty()) {
            log.warn("Empty face ROI provided for preprocessing");
            return faceRoi;
        }

        Mat processed = new Mat();
        try {
            faceRoi.copyTo(processed);

            // Simple safe preprocessing pipeline
            // 1. Resize consistently to ensure standard input (if not full image)
            // Note: If this is used for recognition input, resizing is good.
            // If used for detection, we usually don't resize the ROI again unless needed.
            processed = resizeConsistently(processed);

            // 2. Grayscale conversion for histogram equalization (processed separately but
            // kept in color for some models)
            // But if we return BGR, we need to apply EQ to Value/Intensity channel
            processed = normalizeLighting(processed);

            // 3. Histogram Equalization (Safe version)
            processed = applyHistogramEqualization(processed);

            return processed;
        } catch (Exception e) {
            log.error("Error during face image preprocessing: {}", e.getMessage());
            // Fallback to original
            Mat fallback = new Mat();
            faceRoi.copyTo(fallback);
            return fallback;
        }
    }

    private Mat normalizeLighting(Mat image) {
        Mat normalized = new Mat();
        try {
            // Convert to LAB color space to isolate luminance
            Mat lab = new Mat();
            opencv_imgproc.cvtColor(image, lab, opencv_imgproc.COLOR_BGR2Lab);

            MatVector channels = new MatVector(3);
            opencv_core.split(lab, channels);

            Mat lChannel = channels.get(0);

            // Apply CLAHE to L-channel
            // ClipLimit 2.0 is safe, TileGridSize 8x8 is standard
            CLAHE clahe = opencv_imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(lChannel, lChannel);

            opencv_core.merge(channels, lab);
            opencv_imgproc.cvtColor(lab, normalized, opencv_imgproc.COLOR_Lab2BGR);

            lab.release();
            lChannel.release();
            channels.close(); // Important to release MatVector resource if possible, but let's trust javacpp
                              // for now or explicit release

        } catch (Exception e) {
            log.error("Error in lighting normalization: {}", e.getMessage());
            image.copyTo(normalized);
        }

        return normalized;
    }

    private Mat applyHistogramEqualization(Mat image) {
        Mat equalized = new Mat();
        try {
            Mat ycrcb = new Mat();
            opencv_imgproc.cvtColor(image, ycrcb, opencv_imgproc.COLOR_BGR2YCrCb);

            MatVector channels = new MatVector(3);
            opencv_core.split(ycrcb, channels);

            Mat yChannel = channels.get(0);

            // Standard Histogram Equalization
            opencv_imgproc.equalizeHist(yChannel, yChannel);

            opencv_core.merge(channels, ycrcb);
            opencv_imgproc.cvtColor(ycrcb, equalized, opencv_imgproc.COLOR_YCrCb2BGR);

            ycrcb.release();
            yChannel.release();
            channels.close();

        } catch (Exception e) {
            log.error("Error in histogram equalization: {}", e.getMessage());
            image.copyTo(equalized);
        }

        return equalized;
    }

    // Removed correctColorBalance to avoid CV_64F crashes on CV_8U images

    private Mat resizeConsistently(Mat image) {
        Mat resized = new Mat();
        try {
            opencv_imgproc.resize(image, resized, new Size(TARGET_FACE_SIZE, TARGET_FACE_SIZE), 0, 0,
                    opencv_imgproc.INTER_LANCZOS4);
        } catch (Exception e) {
            log.error("Error in consistent resizing: {}", e.getMessage());
            image.copyTo(resized);
        }

        return resized;
    }

    public double calculateBrightness(Mat image) {
        try {
            Mat gray = new Mat();
            opencv_imgproc.cvtColor(image, gray, opencv_imgproc.COLOR_BGR2GRAY);

            Scalar meanScalar = opencv_core.mean(gray);
            double brightness = meanScalar.get();

            gray.release();
            return brightness;
        } catch (Exception e) {
            log.error("Error calculating brightness: {}", e.getMessage());
            return 128.0;
        }
    }

    public boolean isLowLightCondition(Mat image) {
        double brightness = calculateBrightness(image);
        boolean isLowLight = brightness < BRIGHTNESS_THRESHOLD_LOW;
        log.debug("Lighting condition check - Brightness: {:.2f}, Low light: {}", brightness, isLowLight);
        return isLowLight;
    }

    public boolean isHighLightCondition(Mat image) {
        double brightness = calculateBrightness(image);
        boolean isHighLight = brightness > BRIGHTNESS_THRESHOLD_HIGH;
        log.debug("Lighting condition check - Brightness: {:.2f}, High light/Overexposed: {}", brightness, isHighLight);
        return isHighLight;
    }

    /**
     * Preprocess full image for detection (no resizing)
     */
    public Mat preprocessFullImage(Mat image) {
        if (image == null || image.empty()) {
            return image;
        }

        Mat processed = new Mat();
        try {
            image.copyTo(processed);

            // Only apply lighting normalization
            // Do NOT resize as it reduces detection accuracy for small faces
            processed = normalizeLighting(processed);

            return processed;
        } catch (Exception e) {
            log.error("Error during full image preprocessing: {}", e.getMessage());
            return image;
        }
    }

    public byte[] matToByteArray(Mat mat) {
        if (mat == null || mat.empty()) {
            return new byte[0];
        }

        try (BytePointer buffer = new BytePointer()) {
            opencv_imgcodecs.imencode(".jpg", mat, buffer);
            byte[] byteArray = new byte[(int) buffer.limit()];
            buffer.get(byteArray);
            return byteArray;
        } catch (Exception e) {
            log.error("Error converting Mat to byte array: {}", e.getMessage());
            return new byte[0];
        }
    }
}
