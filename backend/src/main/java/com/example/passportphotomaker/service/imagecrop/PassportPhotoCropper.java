package com.example.passportphotomaker.service.imagecrop;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Specialized class for cropping passport photos according to standard formats.
 * This class handles the cropping of passport photos based on face detection
 * and
 * standard passport photo dimensions.
 */
public class PassportPhotoCropper {
    private boolean debugMode = false;
    private String photoFormat = null;
    private double photoWidth = 0;
    private double photoHeight = 0;
    private String photoUnit = null;
    private int dpi = 300; // Default DPI for passport photos

    /**
     * Constructor with debug mode option
     * 
     * @param debugMode Whether to enable debug mode
     */
    public PassportPhotoCropper(boolean debugMode) {
        this.debugMode = debugMode;
    }

    /**
     * Set the photo format with dimensions and unit
     * 
     * @param format The format name (e.g., "35x45")
     * @param width  The width in the specified unit
     * @param height The height in the specified unit
     * @param unit   The unit of measurement (mm, cm, inch)
     */
    public void setPhotoFormat(String format, double width, double height, String unit) {
        this.photoFormat = format;
        this.photoWidth = width;
        this.photoHeight = height;
        this.photoUnit = unit;

        // Set DPI based on format
        if ("35x45".equals(format) && "mm".equals(unit)) {
            // For 35x45mm format, use 300 DPI (standard for passport photos)
            this.dpi = 300;
        } else if ("2x2".equals(format) && "inch".equals(unit)) {
            // For 2x2 inch format, use 300 DPI
            this.dpi = 300;
        } else if ("5x7".equals(format) && "cm".equals(unit)) {
            // For 5x7 cm format, use 300 DPI
            this.dpi = 300;
        } else if ("33x48".equals(format) && "mm".equals(unit)) {
            // For 33x48 mm format, use 300 DPI
            this.dpi = 300;
        }

        if (debugMode) {
            System.out.println(
                    "Set photo format: " + format + " - " + width + "x" + height + " " + unit + " at " + dpi + " DPI");
        }
    }

    /**
     * Set a custom DPI value
     * 
     * @param dpi The DPI value to use
     */
    public void setCustomDpi(int dpi) {
        this.dpi = dpi;
        if (debugMode) {
            System.out.println("Set custom DPI: " + dpi);
        }
    }

    /**
     * Crop an image to passport photo format based on face detection
     * 
     * @param image    The original image
     * @param faceRect The detected face rectangle
     * @return The cropped image
     */
    public Mat cropToPassportFormat(Mat image, Rect faceRect) {
        if (image == null || image.empty()) {
            throw new IllegalArgumentException("Input image is null or empty");
        }

        if (faceRect == null) {
            throw new IllegalArgumentException("Face rectangle is null");
        }

        if (photoFormat == null) {
            throw new IllegalStateException("Photo format not set. Call setPhotoFormat first.");
        }

        // Calculate target dimensions in pixels based on the specified width, height,
        // and unit
        int targetWidth = calculatePixelSize(photoWidth, photoUnit, dpi);
        int targetHeight = calculatePixelSize(photoHeight, photoUnit, dpi);

        if (debugMode) {
            System.out.println("Target dimensions: " + targetWidth + "x" + targetHeight + " pixels");
            System.out.println("Original image dimensions: " + image.width() + "x" + image.height() + " pixels");
            System.out.println(
                    "Face rectangle: " + faceRect.x + "," + faceRect.y + "," + faceRect.width + "," + faceRect.height);
        }

        // Determine the final aspect ratio
        double targetAspectRatio = (double) targetWidth / targetHeight;
        
        // Calculate dimensions for crop that maintains target aspect ratio
        // Use a larger area around the face to preserve more detail
        double croppingFactor = 2.5; // Increased from 2.0 to get more context
        int cropWidth = (int) (faceRect.width * croppingFactor);
        int cropHeight = (int) (cropWidth / targetAspectRatio);
        
        // If calculated height is too small relative to face, adjust
        if (cropHeight < faceRect.height * 2.0) {
            cropHeight = (int) (faceRect.height * 2.0);
            cropWidth = (int) (cropHeight * targetAspectRatio);
        }
        
        // Center the crop area on the face
        int cropX = faceRect.x + faceRect.width / 2 - cropWidth / 2;
        int cropY = faceRect.y + faceRect.height / 2 - cropHeight / 2;
        
        // Ensure the crop rectangle stays within image boundaries
        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        
        // Adjust dimensions if needed to maintain aspect ratio
        if (cropX + cropWidth > image.width()) {
            cropWidth = image.width() - cropX;
            // Recalculate height to maintain aspect ratio
            cropHeight = (int) (cropWidth / targetAspectRatio);
        }
        
        if (cropY + cropHeight > image.height()) {
            cropHeight = image.height() - cropY;
            // Recalculate width to maintain aspect ratio
            cropWidth = (int) (cropHeight * targetAspectRatio);
        }
        
        // Final check to ensure we're in bounds
        cropWidth = Math.min(cropWidth, image.width() - cropX);
        cropHeight = Math.min(cropHeight, image.height() - cropY);

        // Extract the region of interest
        Mat roi = new Mat(image, new Rect(cropX, cropY, cropWidth, cropHeight));

        // Resize to the target dimensions with higher quality interpolation
        Mat resized = new Mat();
        Imgproc.resize(roi, resized, new Size(targetWidth, targetHeight), 0, 0, Imgproc.INTER_CUBIC);

        // Release resources
        roi.release();

        if (debugMode) {
            System.out.println("Final image dimensions: " + resized.width() + "x" + resized.height() + " pixels");
        }

        return resized;
    }

    /**
     * Calculate pixel size based on physical dimensions and DPI
     * 
     * @param size Physical size in the specified unit
     * @param unit Unit of measurement (mm, cm, inch)
     * @param dpi  Dots per inch
     * @return Pixel size
     */
    private int calculatePixelSize(double size, String unit, int dpi) {
        double unitToInch = getUnitToInchFactor(unit);
        return (int) Math.round(size * dpi / unitToInch);
    }

    /**
     * Convert a unit to inches
     * 
     * @param unit The unit to convert (mm, cm, inch)
     * @return The conversion factor to inches
     */
    private double getUnitToInchFactor(String unit) {
        if ("mm".equalsIgnoreCase(unit)) {
            return 25.4; // 1 inch = 25.4 mm
        } else if ("cm".equalsIgnoreCase(unit)) {
            return 2.54; // 1 inch = 2.54 cm
        } else if ("inch".equalsIgnoreCase(unit)) {
            return 1.0; // 1 inch = 1 inch
        } else {
            throw new IllegalArgumentException("Unsupported unit: " + unit);
        }
    }

    /**
     * Close any resources
     */
    public void close() {
        // Nothing to close in this implementation
    }
}