package com.example.passportphotomaker.service.bgremove;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.web.multipart.MultipartFile;

import ai.djl.translate.TranslateException;

public abstract class BackgroundRemover {
    public abstract Mat removeBackground(File inputFile) throws IOException, TranslateException;

    // New overloaded method to accept Mat input
    public Mat removeBackground(Mat inputMat) throws IOException, TranslateException {
        // Create a temporary file from the Mat
        File tempFile = File.createTempFile("mat-input-", ".jpg");
        try {
            Imgcodecs.imwrite(tempFile.getAbsolutePath(), inputMat);
            return removeBackground(tempFile);
        } finally {
            tempFile.delete();
        }
    }

    /**
     * Close any resources used by the background remover implementation.
     * This method should be called when the background remover is no longer needed.
     */
    public void close() {
        // Default implementation - subclasses can override if needed
    }

    // Default debugMode
    protected boolean debugMode = false;

    // Constructors
    public BackgroundRemover() {
    }

    public BackgroundRemover(boolean debugMode) {
        this.debugMode = debugMode;
    }

    // Shared methods (can be used by all subclasses)
    protected Mat refineMaskEdges(Mat image, Mat mask) {
        // Create hard edges instead of smooth transitions
        
        // Apply threshold to ensure binary mask (0 or 255 values only)
        Mat binaryMask = new Mat();
        Imgproc.threshold(mask, binaryMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Optional: Clean up small artifacts and holes
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Mat cleanedMask = new Mat();
        
        // Close operation fills small holes
        Imgproc.morphologyEx(binaryMask, cleanedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Open operation removes small artifacts
        Imgproc.morphologyEx(cleanedMask, cleanedMask, Imgproc.MORPH_OPEN, kernel);
        
        // Ensure hard edges with another threshold operation
        Mat finalMask = new Mat();
        Imgproc.threshold(cleanedMask, finalMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Clean up
        binaryMask.release();
        cleanedMask.release();
        
        return finalMask;
    }

    protected Mat createAlphaMatte(Mat mask) {
        // For hard edges, just ensure the mask is binary (no alpha transitions)
        Mat binaryMask = new Mat();
        Imgproc.threshold(mask, binaryMask, 127, 255, Imgproc.THRESH_BINARY);
        
        return binaryMask;
    }

    protected Mat createTransparentImage(Mat image, Mat alphaMask) {
        // Create image with transparent background
        Mat result = new Mat();
        Imgproc.cvtColor(image, result, Imgproc.COLOR_BGR2BGRA);

        // Split channels
        List<Mat> channels = new ArrayList<>();
        Core.split(result, channels);

        // Set alpha channel
        alphaMask.copyTo(channels.get(3));

        // Merge channels
        Core.merge(channels, result);

        // Release channels
        for (Mat channel : channels) {
            channel.release();
        }

        return result;
    }

    protected File createTempFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        File tempFile = File.createTempFile("upload-", extension);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        return tempFile;
    }
}