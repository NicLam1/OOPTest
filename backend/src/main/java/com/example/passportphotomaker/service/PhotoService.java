package com.example.passportphotomaker.service;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;


import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;

@Service
public class PhotoService {
    
    private final ResourceLoader resourceLoader;
    
    @Value("${debug.mode:false}")
    private boolean debugMode;
    
    private ZooModel<Image, DetectedObjects> segmentationModel;
    private boolean modelLoaded = false;
    
    // Add these as instance variables to store crop coordinates
    private int cropX = 0;
    private int cropY = 0;
    
    public PhotoService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            // Load OpenCV native library
            nu.pattern.OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully: " + Core.VERSION);
            
            // Load DJL segmentation model
            try {
                loadSegmentationModel();
            } catch (Exception e) {
                System.err.println("Failed to load segmentation model: " + e.getMessage());
                e.printStackTrace();
            }
            
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native code library failed to load: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Initialization error: " + e.getMessage());
        }
    }
    
    private void loadSegmentationModel() throws ModelNotFoundException, MalformedModelException, IOException {
        // Load the U2Net model for salient object detection (good for person segmentation)
        Criteria<Image, DetectedObjects> criteria = 
            Criteria.builder()
                .optApplication(Application.CV.SEMANTIC_SEGMENTATION)
                .setTypes(Image.class, DetectedObjects.class)
                .optModelUrls("djl://ai.djl.pytorch/u2net") // Pre-trained U2Net model
                .build();
                
        segmentationModel = ModelZoo.loadModel(criteria);
        modelLoaded = true;
        System.out.println("Segmentation model loaded successfully");
    }

    public byte[] removeBackground(MultipartFile file) throws IOException {
        // Create temporary file for processing
        File tempFile = createTempFile(file);
        File outputFile = null;
        
        try {
            // First try with advanced model
            if (modelLoaded) {
                try {
                    // Process using DJL model
                    return processWithDJL(tempFile);
                } catch (Exception e) {
                    System.err.println("Error in DJL processing: " + e.getMessage());
                    // Fall back to OpenCV
                }
            }
            
            // Fall back to OpenCV-based processing
            return processWithOpenCV(tempFile);
            
        } catch (Exception e) {
            System.err.println("Error processing image: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Error processing image: " + e.getMessage(), e);
        } finally {
            // Clean up temp files
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
        }
    }
    
    private byte[] processWithDJL(File inputFile) throws IOException, TranslateException {
        // Load image using DJL
        Image image = ImageFactory.getInstance().fromFile(inputFile.toPath());
        
        // Use model to predict segmentation
        try (Predictor<Image, DetectedObjects> predictor = segmentationModel.newPredictor()) {
            // This model returns saliency map rather than actual objects
            DetectedObjects segmentation = predictor.predict(image);
            
            // Save debug output if needed
            if (debugMode) {
                Path debugPath = Paths.get("debug_djl_output.png");
                Image segmentedImage = image.duplicate();
                segmentedImage.drawBoundingBoxes(segmentation);
                segmentedImage.save(Files.newOutputStream(debugPath), "png");
            }
            
            // Convert the DJL segmentation result to OpenCV format for mask refinement
            // Extract mask from the model output
            Mat cvImage = Imgcodecs.imread(inputFile.getAbsolutePath());
            
            // Convert DJL segmentation to OpenCV mask
            Mat mask = createMaskFromDJLOutput(segmentation, cvImage.size());
            
            // Refine mask
            Mat refinedMask = refineMaskEdges(cvImage, mask);
            
            // Create alpha matte for smooth transitions
            Mat alphaMatte = createAlphaMatte(refinedMask);
            
            // Apply the mask to create transparent background
            Mat resultImage = createTransparentImage(cvImage, alphaMatte);
            
            // Draw face detection rectangle on the final image
            Rect faceRect = detectFace(cvImage);
            Imgproc.rectangle(
                resultImage, 
                faceRect, 
                new Scalar(0, 255, 0, 255), // Green with full opacity
                2, // Line thickness
                Imgproc.LINE_8
            );
            
            // Add black border
            Mat borderedResult = addBlackBorder(resultImage, 10);
            
            // Save to temporary output file
            File outputFile = File.createTempFile("processed-", ".png");
            Imgcodecs.imwrite(outputFile.getAbsolutePath(), borderedResult);
            
            // Save debug images if needed
            if (debugMode) {
                Imgcodecs.imwrite("debug_refined_mask.png", refinedMask);
                Imgcodecs.imwrite("debug_alpha_matte.png", alphaMatte);
                Imgcodecs.imwrite("debug_result.png", resultImage);
            }
            
            // Read as bytes
            byte[] resultBytes = Files.readAllBytes(outputFile.toPath());
            
            // Clean up
            cvImage.release();
            mask.release();
            refinedMask.release();
            alphaMatte.release();
            resultImage.release();
            borderedResult.release();
            outputFile.delete();
            
            return resultBytes;
        }
    }
    
    private Mat createMaskFromDJLOutput(DetectedObjects segmentation, Size imageSize) {
        // Create a blank mask
        Mat mask = new Mat(imageSize, CvType.CV_8UC1, new Scalar(0));
        
        // The U2Net model returns different output type than expected
        // Let's handle it properly by extracting probability data
        
        // Check if we have any detections
        if (segmentation.getNumberOfObjects() > 0) {
            try {
                // Since the model returns Classifications instead of DetectedObjects.DetectedObject,
                // we'll create a mask based on the classification probabilities
                
                // Create a mask covering center portion of the image as a fallback
                int centerX = (int)(imageSize.width / 2);
                int centerY = (int)(imageSize.height / 2);
                int centerWidth = (int)(imageSize.width * 0.7); // Cover 70% of width
                int centerHeight = (int)(imageSize.height * 0.9); // Cover 90% of height
                
                Rect centerRect = new Rect(
                    centerX - centerWidth/2,
                    centerY - centerHeight/2,
                    centerWidth,
                    centerHeight
                );
                
                // Make sure the rect is within image bounds
                centerRect.x = Math.max(0, centerRect.x);
                centerRect.y = Math.max(0, centerRect.y);
                centerRect.width = Math.min((int)imageSize.width - centerRect.x, centerRect.width);
                centerRect.height = Math.min((int)imageSize.height - centerRect.y, centerRect.height);
                
                if (centerRect.width > 0 && centerRect.height > 0) {
                    Mat roi = new Mat(mask, centerRect);
                    roi.setTo(new Scalar(255));
                    roi.release();
                }
                
                // Output debug info about what classifications we got
                System.out.println("DJL returned " + segmentation.getNumberOfObjects() + " classes:");
                for (Classifications.Classification c : segmentation.items()) {
                    System.out.println(" - " + c.getClassName() + ": " + c.getProbability());
                }
                
            } catch (Exception e) {
                System.err.println("Error creating mask from DJL output: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // If no detections, use a fallback approach (assume center of image)
            int centerX = (int)(imageSize.width / 2);
            int centerY = (int)(imageSize.height / 2);
            int centerWidth = (int)(imageSize.width * 0.6); // Cover 60% of width
            int centerHeight = (int)(imageSize.height * 0.8); // Cover 80% of height
            
            Rect centerRect = new Rect(
                centerX - centerWidth/2,
                centerY - centerHeight/2,
                centerWidth,
                centerHeight
            );
            
            Mat centerRoi = new Mat(mask, centerRect);
            centerRoi.setTo(new Scalar(255));
            centerRoi.release();
        }
        
        return mask;
    }
    
    private byte[] processWithOpenCV(File inputFile) throws IOException {
        // Load the image
        Mat originalImage = Imgcodecs.imread(inputFile.getAbsolutePath());
        
        if (originalImage.empty()) {
            throw new IOException("Failed to read image");
        }
        
        // Save original for debugging
        if (debugMode) {
            Imgcodecs.imwrite("debug_original.png", originalImage);
        }
        
        // Step 1: Detect face
        Rect faceRect = detectFace(originalImage);
        
        // Step 2: Crop to 7:9 aspect ratio with centered face
        Mat croppedImage = cropToPassportFormat(originalImage, faceRect);
        
        // Adjust faceRect to match new cropped coordinates
        Rect adjustedFaceRect = new Rect(
            faceRect.x - cropX, // Adjust for cropping offset
            faceRect.y - cropY,
            faceRect.width,
            faceRect.height
        );
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_cropped.png", croppedImage);
        }
        
        // Now continue with background removal on the cropped image
        // Create segmentation mask using traditional methods
        Mat mask = createSegmentationMask(croppedImage);
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_initial_mask.png", mask);
        }
        
        // Refine the mask
        Mat refinedMask = refineMaskEdges(croppedImage, mask);
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_refined_mask.png", refinedMask);
        }
        
        // Create alpha matte
        Mat alphaMatte = createAlphaMatte(refinedMask);
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_alpha_matte.png", alphaMatte);
        }
        
        // Create transparent image
        Mat result = createTransparentImage(croppedImage, alphaMatte);
        
        // Draw face detection rectangle on the final image
        Imgproc.rectangle(
            result, 
            adjustedFaceRect, 
            new Scalar(0, 255, 0, 255), // Green with full opacity
            2, // Line thickness
            Imgproc.LINE_8
        );
        
        // NEW STEP: Add black border
        Mat borderedResult = addBlackBorder(result, 10);
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_bordered_result.png", borderedResult);
        }
        
        // Save to temporary file
        File outputFile = File.createTempFile("processed-", ".png");
        Imgcodecs.imwrite(outputFile.getAbsolutePath(), borderedResult);
        
        // Read as bytes
        byte[] resultBytes = Files.readAllBytes(outputFile.toPath());
        
        // Clean up
        originalImage.release();
        croppedImage.release();
        mask.release();
        refinedMask.release();
        alphaMatte.release();
        result.release();
        borderedResult.release();
        outputFile.delete();
        
        return resultBytes;
    }
    
    private Rect detectFace(Mat image) {
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
    
    private Mat cropToPassportFormat(Mat image, Rect faceRect) {
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
        
        return croppedImage;
    }
    
    private Mat createSegmentationMask(Mat image) {
        // Create a segmentation mask using traditional OpenCV methods
        
        // Convert to different color spaces
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
        
        // Detect skin
        Mat skinMask = detectSkin(hsv);
        
        // Detect background
        Mat backgroundMask = detectBackgroundColors(hsv);
        
        // Get foreground (inverse of background)
        Mat foregroundMask = new Mat();
        Core.bitwise_not(backgroundMask, foregroundMask);
        
        // Combine skin and foreground
        Mat combinedMask = new Mat();
        Core.bitwise_or(skinMask, foregroundMask, combinedMask);
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_skin_mask.png", skinMask);
            Imgcodecs.imwrite("debug_bg_mask.png", backgroundMask);
            Imgcodecs.imwrite("debug_combined_mask.png", combinedMask);
        }
        
        // Apply GrabCut for better segmentation
        Mat grabCutMask = new Mat(image.size(), CvType.CV_8UC1, new Scalar(Imgproc.GC_PR_BGD));
        
        // Set probable foreground from our masks
        for (int y = 0; y < combinedMask.rows(); y++) {
            for (int x = 0; x < combinedMask.cols(); x++) {
                if (combinedMask.get(y, x)[0] > 200) {
                    grabCutMask.put(y, x, Imgproc.GC_PR_FGD);
                }
            }
        }
        
        // Mark center as probable foreground (common for portraits)
        int centerX = image.width() / 2;
        int centerY = image.height() / 2;
        int centerWidth = image.width() / 3;
        int centerHeight = image.height() / 2;
        
        Rect centerRect = new Rect(
            centerX - centerWidth/2,
            centerY - centerHeight/2,
            centerWidth,
            centerHeight
        );
        
        for (int y = centerRect.y; y < centerRect.y + centerRect.height; y++) {
            for (int x = centerRect.x; x < centerRect.x + centerRect.width; x++) {
                if (y >= 0 && y < grabCutMask.rows() && x >= 0 && x < grabCutMask.cols()) {
                    grabCutMask.put(y, x, Imgproc.GC_PR_FGD);
                }
            }
        }
        
        // Apply GrabCut
        Mat bgModel = new Mat();
        Mat fgModel = new Mat();
        
        try {
            Imgproc.grabCut(image, grabCutMask, new Rect(), bgModel, fgModel, 5, Imgproc.GC_INIT_WITH_MASK);
        } catch (Exception e) {
            System.err.println("GrabCut error: " + e.getMessage());
        }
        
        // Get foreground mask
        Mat foreground = new Mat();
        Mat probForeground = new Mat();
        Core.compare(grabCutMask, new Scalar(Imgproc.GC_FGD), foreground, Core.CMP_EQ);
        Core.compare(grabCutMask, new Scalar(Imgproc.GC_PR_FGD), probForeground, Core.CMP_EQ);
        
        // Combine definite and probable foreground
        Mat finalMask = new Mat();
        Core.bitwise_or(foreground, probForeground, finalMask);
        
        // Convert to 8-bit
        finalMask.convertTo(finalMask, CvType.CV_8UC1, 255);
        
        // Clean up
        hsv.release();
        skinMask.release();
        backgroundMask.release();
        foregroundMask.release();
        combinedMask.release();
        grabCutMask.release();
        bgModel.release();
        fgModel.release();
        foreground.release();
        probForeground.release();
        
        return finalMask;
    }
    
    private Mat detectSkin(Mat hsv) {
        // Detect skin tones in HSV
        Mat skinMask = new Mat(hsv.size(), CvType.CV_8UC1, new Scalar(0));
        
        // Skin tone ranges
        Mat skinRegion1 = new Mat();
        Mat skinRegion2 = new Mat();
        
        // Lighter skin tones
        Core.inRange(hsv, new Scalar(0, 20, 70), new Scalar(20, 150, 255), skinRegion1);
        
        // Darker skin tones
        Core.inRange(hsv, new Scalar(170, 20, 70), new Scalar(180, 150, 255), skinRegion2);
        
        // Combine ranges
        Core.bitwise_or(skinRegion1, skinRegion2, skinMask);
        
        // Morphological operations to clean up
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.morphologyEx(skinMask, skinMask, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(skinMask, skinMask, Imgproc.MORPH_OPEN, kernel);
        
        // Clean up
        skinRegion1.release();
        skinRegion2.release();
        
        return skinMask;
    }
    
    private Mat detectBackgroundColors(Mat hsv) {
        // Detect common background colors
        Mat blueMask = new Mat();
        Mat greenMask = new Mat();
        Mat whiteMask = new Mat();
        Mat grayMask = new Mat();
        
        // Blue background (common in passport photos)
        Core.inRange(hsv, new Scalar(100, 50, 50), new Scalar(140, 255, 255), blueMask);
        
        // Green background (common in chroma key)
        Core.inRange(hsv, new Scalar(40, 50, 50), new Scalar(80, 255, 255), greenMask);
        
        // White background (studio)
        Core.inRange(hsv, new Scalar(0, 0, 200), new Scalar(180, 30, 255), whiteMask);
        
        // Gray background (neutral)
        Core.inRange(hsv, new Scalar(0, 0, 100), new Scalar(180, 30, 180), grayMask);
        
        // Combine backgrounds
        Mat backgroundMask = new Mat();
        Core.bitwise_or(blueMask, greenMask, backgroundMask);
        Core.bitwise_or(backgroundMask, whiteMask, backgroundMask);
        Core.bitwise_or(backgroundMask, grayMask, backgroundMask);
        
        // Clean up
        blueMask.release();
        greenMask.release();
        whiteMask.release();
        grayMask.release();
        
        return backgroundMask;
    }
    
    private Mat refineMaskEdges(Mat image, Mat mask) {
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
    
    private Mat createAlphaMatte(Mat mask) {
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
    
    private Mat createTransparentImage(Mat image, Mat alphaMask) {
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
    
    private Mat addBlackBorder(Mat image, int borderWidth) {
        // Add a black border around the image
        Mat result = new Mat();
        Core.copyMakeBorder(image, result, borderWidth, borderWidth, borderWidth, borderWidth, 
                           Core.BORDER_CONSTANT, new Scalar(0, 0, 0, 255));
        return result;
    }
    
    private File createTempFile(MultipartFile file) throws IOException {
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
    
    @Override
    protected void finalize() throws Throwable {
        // Clean up resources
        if (segmentationModel != null) {
            segmentationModel.close();
        }
        super.finalize();
    }
}
        
