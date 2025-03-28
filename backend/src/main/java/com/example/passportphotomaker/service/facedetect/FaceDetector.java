package com.example.passportphotomaker.service.facedetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

public class FaceDetector {
    protected boolean debugMode = false;
    private final ResourceLoader resourceLoader;

    public FaceDetector(boolean debugMode, ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Rect detectFace(File inputFile) throws IOException {
        Mat image = Imgcodecs.imread(inputFile.getAbsolutePath());
        if (image.empty()) {
            throw new IOException("Failed to read image");
        }
        // Load the Haar cascade for face detection
        String cascadePath = "haarcascades/haarcascade_frontalface_default.xml";
        Resource cascadeResource = resourceLoader.getResource("classpath:" + cascadePath);
        
        if (!cascadeResource.exists()) {
            System.err.println("Haar cascade file not found. Using fallback face detection.");
            // Return a rectangle in the center of the image as fallback
            int centerX = image.width() / 2;
            int centerY = image.height() / 2;
            int width = image.width() / 4;
            int height = image.height() / 4;
            return new Rect(centerX - width/2, centerY - height/2, width, height);
        }
        
        try {
            // Create a temporary file for the cascade
            File cascadeFile = File.createTempFile("cascade", ".xml");
            FileCopyUtils.copy(cascadeResource.getInputStream(), new FileOutputStream(cascadeFile));
            
            // Load the cascade
            CascadeClassifier faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
            cascadeFile.delete();
            
            // Convert to grayscale for face detection
            Mat gray = new Mat();
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);
            
            // MatOfRect to hold faces
            MatOfRect faces = new MatOfRect();
            // MatOfInt to hold confidence values (will be populated by detectMultiScale)
            MatOfInt confidence = new MatOfInt();
            // MatOfDouble to hold detailed confidence scores
            MatOfDouble confidenceScores = new MatOfDouble();
            
            // Detect faces with confidence scoring
            faceDetector.detectMultiScale3(
                gray,           // Input image
                faces,          // Output detected faces
                confidence,     // Output confidence values
                confidenceScores, // Detailed confidence scores
                1.1,            // Scale factor
                5,              // Min neighbors
                0,              // Flags
                new Size(30, 30), // Min size
                new Size(),     // Max size (no limit)
                true            // Output confidence values
            );
            
            gray.release();
            
            // Get the detected faces and their confidence scores
            Rect[] facesArray = faces.toArray();
            int[] confidenceArray = confidence.toArray();
            
            if (facesArray.length > 0) {
                // Find the face with highest confidence
                Rect bestFace = facesArray[0];
                int bestConfidence = confidenceArray[0];
                
                for (int i = 1; i < facesArray.length; i++) {
                    if (confidenceArray[i] > bestConfidence) {
                        bestFace = facesArray[i];
                        bestConfidence = confidenceArray[i];
                    }
                }
                
                // Log the confidence of the selected face
                System.out.println("Selected face with confidence: " + bestConfidence);
                
                return bestFace;
            }
        } catch (Exception e) {
            System.err.println("Error in face detection: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Fallback to center of image
        int centerX = image.width() / 2;
        int centerY = image.height() / 2;
        int width = image.width() / 4;
        int height = image.height() / 4;
        return new Rect(centerX - width/2, centerY - height/2, width, height);
    }
}
