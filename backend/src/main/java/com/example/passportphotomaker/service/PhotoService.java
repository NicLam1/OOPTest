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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.passportphotomaker.service.bgremove.BackgroundRemover;
import com.example.passportphotomaker.service.bgremove.DJLBackgroundRemover;
import com.example.passportphotomaker.service.bgremove.DirectOnnxBackgroundRemover;
import com.example.passportphotomaker.service.bgremove.OpenCVBackgroundRemover;
import com.example.passportphotomaker.service.facedetect.FaceDetector;

@Service
public class PhotoService {
    private final ResourceLoader resourceLoader;
    private FaceDetector faceDetector;
    private BackgroundRemover bgRemover;
    
    @Value("${debug.mode:false}")
    private boolean debugMode;
    
    @Value("${passport.photo.border.width:10}")
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
                if (debugMode) {
                    e.printStackTrace();
                }
                this.bgRemover = new OpenCVBackgroundRemover(debugMode);
                System.out.println("Fallback to OpenCV Background Remover");
            }
        } else if ("onnx".equalsIgnoreCase(backgroundRemovalMethod)) {
            try {
                this.bgRemover = new DirectOnnxBackgroundRemover(debugMode);
                System.out.println("Using Direct ONNX Background Remover (explicitly configured)");
            } catch (Exception e) {
                System.err.println("Failed to initialize Direct ONNX Background Remover: " + e.getMessage());
                if (debugMode) {
                    e.printStackTrace();
                }
                this.bgRemover = new OpenCVBackgroundRemover(debugMode);
                System.out.println("Fallback to OpenCV Background Remover");
            }
        } else {
            // Auto mode - try to find the best available background remover
            // Create necessary directories first
            createModelDirectories();
            
            // Use OpenCV by default as it's more reliable without external dependencies
            System.out.println("Using OpenCV Background Remover by default");
            this.bgRemover = new OpenCVBackgroundRemover(debugMode);
            
            // Check for ONNX model file
            File modelsDir = new File("models");
            File onnxModelFile = new File(modelsDir, "u2net.onnx");
            
            if (onnxModelFile.exists()) {
                try {
                    // Try direct ONNX implementation first
                    this.bgRemover = new DirectOnnxBackgroundRemover(debugMode);
                    System.out.println("Successfully switched to Direct ONNX Background Remover (recommended)");
                } catch (Exception e) {
                    System.err.println("Failed to initialize Direct ONNX remover: " + e.getMessage());
                    if (debugMode) {
                        e.printStackTrace();
                    }
                    
                    // Fall back to DJL if direct ONNX fails
                    try {
                        this.bgRemover = new DJLBackgroundRemover(debugMode);
                        System.out.println("Successfully switched to DJL Background Remover (fallback)");
                    } catch (Exception ex) {
                        System.err.println("Failed to initialize DJL remover: " + ex.getMessage());
                        if (debugMode) {
                            ex.printStackTrace();
                        }
                        System.out.println("Using OpenCV Background Remover (last resort)");
                    }
                }
            } else {
                // Check for PT model file for DJL
                File ptModelFile = new File(modelsDir, "u2net.pt");
                if (ptModelFile.exists()) {
                    try {
                        this.bgRemover = new DJLBackgroundRemover(debugMode);
                        System.out.println("Successfully switched to DJL Background Remover");
                    } catch (Exception e) {
                        System.err.println("Failed to initialize DJL remover: " + e.getMessage());
                        if (debugMode) {
                            e.printStackTrace();
                        }
                        System.out.println("Using OpenCV Background Remover (fallback)");
                    }
                }
            }
        }
    }

    // Create model directories to ensure they exist
    private void createModelDirectories() {
        // Create models directory
        File modelsDir = new File("models");
        if (!modelsDir.exists()) {
            boolean created = modelsDir.mkdirs();
            if (created) {
                System.out.println("Created models directory at: " + modelsDir.getAbsolutePath());
            }
        }
        
        // Create cascades directory
        File cascadesDir = new File("cascades");
        if (!cascadesDir.exists()) {
            boolean created = cascadesDir.mkdirs();
            if (created) {
                System.out.println("Created cascades directory at: " + cascadesDir.getAbsolutePath());
            }
        }
        
        // Check for existing model files
        File standardModelPt = new File(modelsDir, "u2net.pt");
        File standardModelOnnx = new File(modelsDir, "u2net.onnx");
        File cascadeFile = new File(cascadesDir, "haarcascade_frontalface_default.xml");
        
        // Print a helpful message if files are missing
        if ((!standardModelPt.exists() && !standardModelOnnx.exists()) || !cascadeFile.exists()) {
            System.out.println("\n=========================================");
            System.out.println("RECOMMENDED FILES FOR BETTER RESULTS:");
            
            if (!standardModelPt.exists() && !standardModelOnnx.exists()) {
                System.out.println("1. U2Net model (recommended for best background removal):");
                System.out.println("   Download from: https://github.com/danielgatis/rembg/raw/main/rembg/sessions/u2net.onnx");
                System.out.println("   Save to: " + standardModelOnnx.getAbsolutePath() + " (keep the .onnx extension)");
                System.out.println("   Note: The ONNX format is preferred for better compatibility");
            }
            
            if (!cascadeFile.exists()) {
                System.out.println("2. Face cascade file (needed for better face detection):");
                System.out.println("   Download from: https://github.com/opencv/opencv/raw/master/data/haarcascades/haarcascade_frontalface_default.xml");
                System.out.println("   Save to: " + cascadeFile.getAbsolutePath());
            }
            
            System.out.println("\nNote: The application will use OpenCV for background removal if the model files are not found.");
            System.out.println("=========================================\n");
        }
    }

    public byte[] processImage(MultipartFile file, String format) throws IOException {
        // Validate input file (this part stays the same)
        validateInputFile(file);
        
        // Create temporary file for processing
        File tempFile = uploadToTempFile(file);
        File outputFile = null;
        Mat processedImage = null;
        Mat borderedImage = null;
        
        try {
            // START OF IMAGE PROCESSING -----------------------------------
            // Remove Background
            processedImage = bgRemover.removeBackground(tempFile);
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
            String extension = format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg") ? ".jpg" : ".png";
            
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
    

    // Helper methods for better error handling and resource management
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
        Scalar borderColor = new Scalar(255, 255, 255, 255);  // White for JPEG
        
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
}
        
