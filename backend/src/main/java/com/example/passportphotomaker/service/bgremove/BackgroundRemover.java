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
import org.opencv.imgproc.Imgproc;
import org.springframework.web.multipart.MultipartFile;

import ai.djl.translate.TranslateException;

public abstract class BackgroundRemover {
    public abstract Mat removeBackground(File inputFile) throws IOException, TranslateException;

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
    public BackgroundRemover() {}
    public BackgroundRemover(boolean debugMode) {
        this.debugMode = debugMode;
    }

    // Shared methods (can be used by all subclasses)
    protected Mat refineMaskEdges(Mat image, Mat mask) {
        // Refine mask edges for better results
        
        // Apply bilateral filter to smooth while preserving edges
        Mat refinedMask = new Mat();
        Imgproc.bilateralFilter(mask, refinedMask, 9, 75, 75);
        
        // Morphological operations
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.morphologyEx(refinedMask, refinedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Edge-aware filtering
        Mat edges = new Mat();
        Imgproc.Canny(image, edges, 100, 200);
        
        // Dilate edges
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
        
        // Use edges to refine boundaries
        Mat edgeRefinedMask = new Mat();
        Mat scalarMat = new Mat(mask.size(), mask.type(), Scalar.all(255));
        Core.bitwise_and(refinedMask, scalarMat, edgeRefinedMask, edges);
        
        // Combine with original
        Mat finalMask = new Mat();
        Core.bitwise_or(refinedMask, edgeRefinedMask, finalMask);
        
        // Clean up
        refinedMask.release();
        edges.release();
        edgeRefinedMask.release();
        scalarMat.release();
        
        return finalMask;
    }
    protected Mat createAlphaMatte(Mat mask) {
        // Create alpha matte with smooth transitions
        Mat alphaMatte = new Mat();
        mask.convertTo(alphaMatte, CvType.CV_32FC1, 1.0/255.0);
        
        // Apply Gaussian blur for smooth transitions
        Mat blurredMask = new Mat();
        Imgproc.GaussianBlur(alphaMatte, blurredMask, new Size(9, 9), 0);
        
        // Create gradient mask
        Mat gradientMask = new Mat();
        Imgproc.Laplacian(mask, gradientMask, CvType.CV_32F);
        Core.convertScaleAbs(gradientMask, gradientMask);
        
        // Normalize gradient
        Core.normalize(gradientMask, gradientMask, 0, 1, Core.NORM_MINMAX, CvType.CV_32F);
        
        // Blend masks
        Mat result = new Mat();
        Core.addWeighted(alphaMatte, 0.7, blurredMask, 0.3, 0, result);
        
        // Convert to 8-bit
        result.convertTo(result, CvType.CV_8UC1, 255);
        
        // Clean up
        alphaMatte.release();
        blurredMask.release();
        gradientMask.release();
        
        return result;
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