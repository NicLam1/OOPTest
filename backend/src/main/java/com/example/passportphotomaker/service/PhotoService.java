package com.example.passportphotomaker.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import javax.annotation.PostConstruct;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.passportphotomaker.service.bgremove.BackgroundRemover;
import com.example.passportphotomaker.service.bgremove.DJLBackgroundRemover;
import com.example.passportphotomaker.service.bgremove.OpenCVBackgroundRemover;
import com.example.passportphotomaker.service.facedetect.FaceDetector;
import com.example.passportphotomaker.service.imagecrop.ImageCropper;

@Service
public class PhotoService {
    private final ResourceLoader resourceLoader;
    private FaceDetector faceDetector;
    private BackgroundRemover bgRemover;
    private ImageCropper cropper;
    
    @Value("${debug.mode:false}")
    private boolean debugMode;

    @PostConstruct
    public void init() { // Loads OpenCV
        try {
            // Load OpenCV native library
            nu.pattern.OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully: " + Core.VERSION);            
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native code library failed to load: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Initialization error: " + e.getMessage());
        }
    }
    
    public PhotoService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;

        // Face detector instantiation
        this.faceDetector = new FaceDetector(debugMode, resourceLoader);
        
        // Image cropper instantiation
        this.cropper = new ImageCropper(debugMode);

        // Background remover instantiation
        try {
            // Try instantiating DJLBackgroundRemover
            this.bgRemover = new DJLBackgroundRemover(debugMode);
        } catch (Exception e) {
            System.err.println("Failed to instantiate DJLBackgroundRemover: " + e.getMessage());
            e.printStackTrace();

            // Fallback on OpenCV Background Remover
            this.bgRemover = new OpenCVBackgroundRemover(true);
            System.out.println("Instantiated OpenCVBackgroundRemover");
        }
    }

    public byte[] passportifyImage(MultipartFile file) throws IOException {
        // Create temporary file for processing
        File tempFile = uploadToTempFile(file);
        File outputFile = null;
        Mat bglessImage = null;
        Mat croppedImage = null;
        Mat borderedImage = null;
        
        try {
            // START OF IMAGE PROCESSING -----------------------------------
            // Remove Background
            bglessImage = bgRemover.removeBackground(tempFile);
            // Detect Face
            Rect faceRect = faceDetector.detectFace(tempFile);
            // Crop Image
            croppedImage = cropper.cropToPassportFormat(bglessImage, faceRect);
            // Add Black Border
            borderedImage = addBlackBorder(croppedImage, 10); // just for testing
            // END OF IMAGE PROCESSING -----------------------------------

            // Save to temporary output file
            outputFile = File.createTempFile("processed-", ".png");
            Imgcodecs.imwrite(outputFile.getAbsolutePath(), borderedImage);
            // Read as bytes
            byte[] resultBytes = Files.readAllBytes(outputFile.toPath());

            return resultBytes;
        } catch (Exception e) {
            System.err.println("Error processing image: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Error processing image: " + e.getMessage(), e);
        } finally {
            // Clean up Mats
            if (bglessImage!=null) {
                bglessImage.release();
            }
            if (croppedImage!=null) {
                croppedImage.release();
            }
            if (borderedImage!=null) {
                borderedImage.release();
            }
            // Clean up temp files
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
        }
    }

    private Mat addBlackBorder(Mat image, int borderWidth) {
        // Add a black border around the image
        Mat result = new Mat();
        Core.copyMakeBorder(image, result, borderWidth, borderWidth, borderWidth, borderWidth, 
                           Core.BORDER_CONSTANT, new Scalar(0, 0, 0, 255));
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
        }
        return tempFile;
    }
}
        
