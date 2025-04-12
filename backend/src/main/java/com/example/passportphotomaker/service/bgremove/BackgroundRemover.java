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
        // Create mostly hard edges with very slight feathering
        
        // Step 1: Apply threshold to ensure binary mask (0 or 255 values only)
        Mat binaryMask = new Mat();
        Imgproc.threshold(mask, binaryMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Step 2: Clean up small artifacts and holes
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Mat cleanedMask = new Mat();
        
        // Close operation fills small holes
        Imgproc.morphologyEx(binaryMask, cleanedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Open operation removes small artifacts
        Imgproc.morphologyEx(cleanedMask, cleanedMask, Imgproc.MORPH_OPEN, kernel);
        
        // Step 3: Extract edge region only (where feathering will be applied)
        Mat dilatedMask = new Mat();
        Mat erodedMask = new Mat();
        
        // Create dilated mask (slightly expanded)
        Imgproc.dilate(cleanedMask, dilatedMask, kernel);
        
        // Create eroded mask (slightly contracted)
        Imgproc.erode(cleanedMask, erodedMask, kernel);
        
        // Edge mask = dilated - eroded
        Mat edgeMask = new Mat();
        Core.subtract(dilatedMask, erodedMask, edgeMask);
        
        // Step 4: Apply very slight blur to the original mask
        Mat slightlyBlurredMask = new Mat();
        Imgproc.GaussianBlur(cleanedMask, slightlyBlurredMask, new Size(3, 3), 0.8);
        
        // Step 5: Combine - use slightly blurred mask only at the edges, keep the rest binary
        Mat result = cleanedMask.clone();
        slightlyBlurredMask.copyTo(result, edgeMask);
        
        // Clean up
        binaryMask.release();
        cleanedMask.release();
        dilatedMask.release();
        erodedMask.release();
        edgeMask.release();
        slightlyBlurredMask.release();
        
        return result;
    }

    protected Mat createAlphaMatte(Mat mask) {
        // For mostly hard edges with very slight feathering at the boundaries
        
        // Step 1: Create a binary mask as base
        Mat binaryMask = new Mat();
        Imgproc.threshold(mask, binaryMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Step 2: Extract edge region only
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(4, 4));
        Mat dilatedMask = new Mat();
        Mat erodedMask = new Mat();
        
        // Dilate - slightly expand
        Imgproc.dilate(binaryMask, dilatedMask, kernel);
        
        // Erode - slightly contract
        Imgproc.erode(binaryMask, erodedMask, kernel);
        
        // Edge mask = dilated - eroded (narrow band around the edge)
        Mat edgeMask = new Mat();
        Core.subtract(dilatedMask, erodedMask, edgeMask);
        
        // Step 3: Apply very subtle feathering to the edge regions
        Mat featheredMask = binaryMask.clone();
        
        // Apply very light blur only to edge regions
        Mat blurredEdges = new Mat();
        Imgproc.GaussianBlur(binaryMask, blurredEdges, new Size(10, 10), 1.5);
        
        // Copy the blurred edges to the edge mask region
        blurredEdges.copyTo(featheredMask, edgeMask);
        
        // Clean up
        binaryMask.release();
        dilatedMask.release();
        erodedMask.release();
        edgeMask.release();
        blurredEdges.release();
        
        return featheredMask;
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