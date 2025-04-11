package com.example.passportphotomaker.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.passportphotomaker.service.bgchange.BackgroundChanger;
import com.example.passportphotomaker.service.bgremove.BackgroundRemover;
import com.example.passportphotomaker.service.bgremove.DJLBackgroundRemover;
import com.example.passportphotomaker.service.bgremove.DirectOnnxBackgroundRemover;
import com.example.passportphotomaker.service.bgremove.OpenCVBackgroundRemover;
import com.example.passportphotomaker.service.facedetect.FaceDetector;
import com.example.passportphotomaker.service.imagecrop.PassportPhotoCropper;
import com.example.passportphotomaker.service.imageedit.ImageAdjuster;

@Service
public class PhotoService {
    private final ResourceLoader resourceLoader;
    private FaceDetector faceDetector;
    private BackgroundRemover bgRemover;
    private BackgroundChanger bgChanger;

    @Value("${debug.mode:false}")
    private boolean debugMode;

    @Value("${passport.photo.border.width:0}")
    private int borderWidth;

    @Value("${background.removal.method:auto}")
    private String backgroundRemovalMethod;

    @PostConstruct
    public void init() { // Loads OpenCV
        try {
            // Load OpenCV native library
            nu.pattern.OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully: " + Core.VERSION);

            // Initialize services after OpenCV is loaded
            initializeServices();
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native code library failed to load: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Initialization error: " + e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        // Clean up resources when the application is shutting down
        System.out.println("Cleaning up resources in PhotoService");

        if (bgRemover != null) {
            try {
                bgRemover.close();
                System.out.println("Background remover resources released");
            } catch (Exception e) {
                System.err.println("Error closing background remover: " + e.getMessage());
            }
        }

        if (faceDetector != null) {
            try {
                faceDetector.close();
                System.out.println("Face detector resources released");
            } catch (Exception e) {
                System.err.println("Error closing face detector: " + e.getMessage());
            }
        }
    }

    public PhotoService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private void initializeServices() {
        // Face detector instantiation
        this.faceDetector = new FaceDetector(debugMode, resourceLoader);

        // Background remover instantiation based on configuration
        initializeBackgroundRemover();
    }

    private void initializeBackgroundRemover() {
        // Choose background remover based on configuration
        if ("opencv".equalsIgnoreCase(backgroundRemovalMethod)) {
            this.bgRemover = new OpenCVBackgroundRemover(debugMode);
            System.out.println("Using OpenCV Background Remover (explicitly configured)");
        } else if ("djl".equalsIgnoreCase(backgroundRemovalMethod)) {
            try {
                this.bgRemover = new DJLBackgroundRemover(debugMode);
                System.out.println("Using DJL Background Remover");
            } catch (Exception e) {
                System.err.println("Failed to initialize DJL Background Remover: " + e.getMessage());
                // Fall back to OpenCV
                this.bgRemover = new OpenCVBackgroundRemover(debugMode);
                System.out.println("Falling back to OpenCV Background Remover");
            }
        } else {
            // Try DirectONNX first (preferred method)
            try {
                this.bgRemover = new DirectOnnxBackgroundRemover(debugMode);
                System.out.println("Using DirectONNX Background Remover (auto-selected)");
            } catch (Exception e) {
                System.err.println("Failed to initialize DirectONNX Background Remover: " + e.getMessage());
                // Try DJL next
                try {
                    this.bgRemover = new DJLBackgroundRemover(debugMode);
                    System.out.println("Using DJL Background Remover (auto-selected)");
                } catch (Exception e2) {
                    System.err.println("Failed to initialize DJL Background Remover: " + e2.getMessage());
                    // Fall back to OpenCV
                    this.bgRemover = new OpenCVBackgroundRemover(debugMode);
                    System.out.println("Falling back to OpenCV Background Remover (auto-selected)");
                }
            }
        }
    }

    public byte[] processImage(MultipartFile file, String photoFormat, Double photoWidth, Double photoHeight,
            String photoUnit) throws IOException {
        File tempFile = null;
        File outputFile = null;
        Mat processedImage = null;
        Mat borderedImage = null;

        try {
            // Validate input file
            validateInputFile(file);

            // Save uploaded file to temporary location
            tempFile = uploadToTempFile(file);

            // Load image using OpenCV
            Mat originalImage = Imgcodecs.imread(tempFile.getAbsolutePath());
            if (originalImage.empty()) {
                throw new IOException("Failed to read image");
            }

            // Size Validation
            if (photoFormat != null && photoWidth != null && photoHeight != null && photoUnit != null) {
                int[] expectedSize = calculatePixelSize(photoWidth, photoHeight, photoUnit, 300);
                int expectedWidth = expectedSize[0];
                int expectedHeight = expectedSize[1];

                if (originalImage.width() != expectedWidth || originalImage.height() != expectedHeight) {
                    throw new IOException("Uploaded image does not match expected dimensions: " +
                            expectedWidth + "x" + expectedHeight + " pixels @ 300 DPI, but got " +
                            originalImage.width() + "x" + originalImage.height());
                }
            }

            if (debugMode) {
                System.out.println("Original image dimensions: " + originalImage.width() + "x" + originalImage.height()
                        + " pixels");
            }

            // Since the image is already cropped in the frontend, we'll just resize it to
            // the target dimensions
            // if photo format is specified
            Mat resizedImage = originalImage;

            if (photoFormat != null && photoWidth != null && photoHeight != null && photoUnit != null) {
                // Use the specialized passport photo cropper to resize to the correct
                // dimensions
                PassportPhotoCropper photoCropper = new PassportPhotoCropper(debugMode);
                photoCropper.setPhotoFormat(photoFormat, photoWidth, photoHeight, photoUnit);

                // Create a dummy face rectangle in the center of the image
                // This is just for the cropper to calculate the target dimensions
                Rect centerRect = new Rect(
                        originalImage.width() / 4,
                        originalImage.height() / 4,
                        originalImage.width() / 2,
                        originalImage.height() / 2);

                // Resize the image to the target dimensions
                resizedImage = photoCropper.cropToPassportFormat(originalImage, centerRect);
                originalImage.release(); // Release the original image to free memory

                if (debugMode) {
                    System.out.println("Resized image dimensions: " + resizedImage.width() + "x" + resizedImage.height()
                            + " pixels");
                    System.out.println("Expected dimensions at 300 DPI: " +
                            (int) (photoWidth * 300 / getUnitToInchFactor(photoUnit)) + "x" +
                            (int) (photoHeight * 300 / getUnitToInchFactor(photoUnit)) + " pixels");
                }
            }

            // START OF IMAGE PROCESSING -----------------------------------
            // Remove Background
            processedImage = bgRemover.removeBackground(resizedImage);
            if (processedImage == null || processedImage.empty()) {
                throw new IOException("Background removal failed to produce a valid image");
            }

            // Add Border (this remains the same)
            borderedImage = addBorder(processedImage, borderWidth);
            if (borderedImage == null || borderedImage.empty()) {
                throw new IOException("Failed to add border to image");
            }
            // END OF IMAGE PROCESSING -----------------------------------

            // Choose the extension based on the requested format
            String extension = ".png"; // Default to PNG

            // Save to temporary output file
            outputFile = File.createTempFile("processed-", extension);
            boolean writeSuccess = Imgcodecs.imwrite(outputFile.getAbsolutePath(), borderedImage);

            if (!writeSuccess) {
                throw new IOException("Failed to write output image to disk");
            }

            // Read the saved image as bytes
            byte[] resultBytes = Files.readAllBytes(outputFile.toPath());

            if (resultBytes == null || resultBytes.length == 0) {
                throw new IOException("Failed to read output image data");
            }

            return resultBytes;
        } catch (Exception e) {
            System.err.println("Error processing image: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            throw new IOException("Error processing image: " + e.getMessage(), e);
        } finally {
            // Clean up Mats and temp files
            releaseMatSafely(processedImage);
            releaseMatSafely(borderedImage);
            deleteTempFileSafely(tempFile);
            deleteTempFileSafely(outputFile);
        }
    }

    // Helper methods for better error handling and resource cleanup
    private void validateInputFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Input file is empty or null");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Invalid file type. Only image files are accepted");
        }

        long fileSize = file.getSize();
        if (fileSize <= 0 || fileSize > 10 * 1024 * 1024) { // 10MB limit
            throw new IOException("Invalid file size. File must be between 1 byte and 10MB");
        }
    }

    private void releaseMatSafely(Mat mat) {
        if (mat != null) {
            try {
                mat.release();
            } catch (Exception e) {
                if (debugMode) {
                    System.err.println("Error releasing Mat: " + e.getMessage());
                }
            }
        }
    }

    private void deleteTempFileSafely(File file) {
        if (file != null && file.exists()) {
            try {
                boolean deleted = file.delete();
                if (!deleted && debugMode) {
                    System.err.println("Failed to delete temporary file: " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                if (debugMode) {
                    System.err.println("Error deleting temporary file: " + e.getMessage());
                }
            }
        }
    }

    private Mat addBorder(Mat image, int borderWidth) {
        // Add a border around the image with configurable width
        Mat result = new Mat();

        // If it's a PNG with transparency, handle it as PNG
        // If it's a JPEG, fill the border with white (RGB: 255, 255, 255)
        Scalar borderColor = new Scalar(255, 255, 255, 255); // White for JPEG

        Core.copyMakeBorder(image, result, borderWidth, borderWidth, borderWidth, borderWidth,
                Core.BORDER_CONSTANT, borderColor);
        return result;
    }

    private File uploadToTempFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        File tempFile = File.createTempFile("upload-", extension);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        } catch (IOException e) {
            deleteTempFileSafely(tempFile);
            throw new IOException("Failed to create temporary file: " + e.getMessage(), e);
        }
        return tempFile;
    }

    private int[] calculatePixelSize(double width, double height, String unit, int dpi) {
        double unitToInch;

        switch (unit.toLowerCase()) {
            case "mm":
                unitToInch = 25.4;
                break;
            case "cm":
                unitToInch = 2.54;
                break;
            case "inch":
                unitToInch = 1.0;
                break;
            default:
                throw new IllegalArgumentException("Unsupported unit: " + unit);
        }

        int pixelWidth = (int) Math.round(width * dpi / unitToInch);
        int pixelHeight = (int) Math.round(height * dpi / unitToInch);
        return new int[] { pixelWidth, pixelHeight };
    }

    // public byte[] BackgroundChanger(byte[] transparentImg, String colour) throws IOException {
    //     Color hex_colour = Color.decode(colour);
    //     byte[] resultsBytes = bgChanger.addSolidColorBackground(transparentImg, hex_colour);
    //     return resultsBytes;
    // }

    // public byte[] BackgroundChanger(byte[] transparentImg, MultipartFile background) throws IOException {
    //     byte[] resultsBytes = bgChanger.addBackgroundImg(transparentImg, background);
    //     return resultsBytes;
    // }

    public byte[] BackgroundChanger(byte[] transparentImg, String background) throws IOException {
        byte[] resultsBytes = BackgroundChanger.addBackgroundImgFromString(transparentImg, background);
        return resultsBytes;
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
 * Applies brightness, contrast, and saturation adjustments to an image sent from the frontend.
 *
 * @param file Multipart image file (with transparent background)
 * @param brightness Brightness level (-100 to 100)
 * @param contrast Contrast level (1.0 = no change)
 * @param saturation Saturation level (1.0 = no change)
 * @return The adjusted image as byte array
 * @throws IOException If processing fails
 */
    public byte[] adjustImage(MultipartFile file, double brightness, double contrast, double saturation) throws IOException {
        File tempFile = null;
        File outputFile = null;
        Mat image = null;
        Mat adjusted = null;

        try {
            // Validate and upload to temp
            validateInputFile(file);
            tempFile = uploadToTempFile(file);

            // Read with OpenCV
            image = Imgcodecs.imread(tempFile.getAbsolutePath(), Imgcodecs.IMREAD_UNCHANGED);
            if (image.empty()) {
                throw new IOException("Failed to read image for adjustment");
            }

            // Apply adjustments
            adjusted = ImageAdjuster.applyAdjustments(image, brightness, contrast, saturation);

            // Save output
            outputFile = File.createTempFile("adjusted-", ".png");
            boolean success = Imgcodecs.imwrite(outputFile.getAbsolutePath(), adjusted);
            if (!success) {
                throw new IOException("Failed to save adjusted image");
            }

            return Files.readAllBytes(outputFile.toPath());

        } catch (Exception e) {
            System.err.println("Error adjusting image: " + e.getMessage());
            if (debugMode) e.printStackTrace();
            throw new IOException("Error adjusting image", e);
        } finally {
            releaseMatSafely(image);
            releaseMatSafely(adjusted);
            deleteTempFileSafely(tempFile);
            deleteTempFileSafely(outputFile);
        }
    }

}