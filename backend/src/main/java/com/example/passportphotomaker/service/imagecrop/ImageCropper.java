package com.example.passportphotomaker.service.imagecrop;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class ImageCropper {
    protected boolean debugMode = false;
    private int cropX;
    private int cropY;

    public ImageCropper() {}
    public ImageCropper(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public Mat cropToPassportFormat(Mat image, Rect faceRect) {
        // Calculate face center
        int faceCenterX = faceRect.x + faceRect.width / 2;
        int faceCenterY = faceRect.y + faceRect.height / 2;
        
        // Ensure equal spacing from all sides
        // For 7:9 aspect ratio, the width is 7/9 of the height
        // Determine the maximum dimension needed to ensure entire face is captured with padding
        
        // 1. Calculate minimum size needed with equal spacing on all sides
        double paddingFactor = 1.5; // This ensures there's at least 50% padding around the face
        int minWidth = (int)(faceRect.width * paddingFactor);
        int minHeight = (int)(faceRect.height * paddingFactor);
        
        // 2. Adjust for 7:9 aspect ratio (width:height)
        int targetHeight, targetWidth;
        
        // If current aspect is wider than target
        if (minWidth > minHeight * 7.0 / 9.0) {
            targetWidth = minWidth;
            targetHeight = (int)(targetWidth * 9.0 / 7.0);
        } else {
            targetHeight = minHeight;
            targetWidth = (int)(targetHeight * 7.0 / 9.0);
        }
        
        // Calculate crop coordinates centered on face
        int cropX = faceCenterX - targetWidth / 2;
        int cropY = faceCenterY - targetHeight / 2;
        
        // Ensure crop rectangle is within image bounds
        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        
        // Adjust if we're going out of bounds on the right or bottom
        if (cropX + targetWidth > image.width()) {
            cropX = image.width() - targetWidth;
        }
        
        if (cropY + targetHeight > image.height()) {
            cropY = image.height() - targetHeight;
        }
        
        // Final sanity check (could happen with small images)
        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        targetWidth = Math.min(targetWidth, image.width() - cropX);
        targetHeight = Math.min(targetHeight, image.height() - cropY);
        
        // Store crop coordinates for later use
        this.cropX = cropX;
        this.cropY = cropY;
        
        // Create crop rectangle
        Rect cropRect = new Rect(cropX, cropY, targetWidth, targetHeight);
        
        // Perform the crop
        Mat croppedImage = new Mat(image, cropRect);

        if (debugMode) {
            // Draw green face identifier if debugging
            Rect adjustedFaceRect = new Rect(
                faceRect.x - cropX, // Adjust for cropping offset
                faceRect.y - cropY,
                faceRect.width,
                faceRect.height
            );
            Imgproc.rectangle(
                croppedImage, 
                adjustedFaceRect, 
                new Scalar(0, 255, 0, 255), // Green with full opacity
                2, // Line thickness
                Imgproc.LINE_8
            );
            
        }
        
        return croppedImage;
    }
}
