package com.example.passportphotomaker.service.bgremove;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

public class OpenCVBackgroundRemover extends BackgroundRemover {
    // Path to face detection cascade classifier
    private CascadeClassifier faceDetector;
    private static final String FACE_CASCADE = "haarcascade_frontalface_default.xml";

    // Constructors
    public OpenCVBackgroundRemover() {
        initFaceDetector();
    }
    
    public OpenCVBackgroundRemover(boolean debugMode) {
        super(debugMode);
        initFaceDetector();
    }
    
    private void initFaceDetector() {
        try {
            // Try multiple locations for the cascade file
            
            // 1. First try resources directory
            InputStream resourceStream = getClass().getResourceAsStream("/cascades/" + FACE_CASCADE);
            
            // 2. Try local cascades directory
            File localCascadeDir = new File("cascades");
            File localCascadeFile = new File(localCascadeDir, FACE_CASCADE);
            
            // Create cascades directory if it doesn't exist
            if (!localCascadeDir.exists()) {
                localCascadeDir.mkdirs();
                System.out.println("Created cascades directory at: " + localCascadeDir.getAbsolutePath());
                System.out.println("Please place haarcascade_frontalface_default.xml in this directory");
            }
            
            if (resourceStream != null) {
                // Load from resources
                File tempFile = File.createTempFile("cascade", ".xml");
                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = resourceStream.read(buffer)) != -1) {
                        out.write(buffer, 0, length);
                    }
                }
                
                faceDetector = new CascadeClassifier(tempFile.getAbsolutePath());
                tempFile.delete();
                
                System.out.println("Loaded face cascade from resources");
            } else if (localCascadeFile.exists()) {
                // Load from local file
                System.out.println("Loading face cascade from: " + localCascadeFile.getAbsolutePath());
                faceDetector = new CascadeClassifier(localCascadeFile.getAbsolutePath());
                System.out.println("Successfully loaded face cascade from local file");
            } else {
                // Cascade file not found
                System.out.println("\n=========================================");
                System.out.println("FACE CASCADE FILE NOT FOUND - PLEASE DOWNLOAD:");
                System.out.println("Download from: https://github.com/opencv/opencv/raw/master/data/haarcascades/haarcascade_frontalface_default.xml");
                System.out.println("Save it to the 'cascades' directory");
                System.out.println("=========================================\n");
                
                faceDetector = null;
            }
            
            if (faceDetector != null && faceDetector.empty()) {
                System.err.println("Failed to load face cascade classifier. Using fallback methods.");
                faceDetector = null;
            }
        } catch (Exception e) {
            System.err.println("Error initializing face detector: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            faceDetector = null;
        }
    }

    // Main method to implement
    @Override
    public Mat removeBackground(File inputFile) throws IOException {
        // Load the image
        Mat originalImage = Imgcodecs.imread(inputFile.getAbsolutePath());
        if (originalImage.empty()) {
            throw new IOException("Failed to read image");
        }

        // Background removal
        Mat mask;
        
        // Try with face detection first for better segmentation
        if (faceDetector != null && !faceDetector.empty()) {
            mask = createSegmentationMaskWithFaceDetection(originalImage);
        } else {
            mask = createSegmentationMask(originalImage);
        }
        
        Mat refinedMask = refineMaskEdges(originalImage, mask);
        Mat alphaMatte = createAlphaMatte(refinedMask);
        Mat result = createTransparentImage(originalImage, alphaMatte);
        
        // Intermediary files for debugging purposes
        if (debugMode) {
            Imgcodecs.imwrite("debug_original.png", originalImage);
            Imgcodecs.imwrite("debug_initial_mask.png", mask);
            Imgcodecs.imwrite("debug_refined_mask.png", refinedMask);
            Imgcodecs.imwrite("debug_alpha_matte.png", alphaMatte);
            Imgcodecs.imwrite("debug_result.png", result);
        }
        
        // Clean up
        originalImage.release();
        mask.release();
        refinedMask.release();
        alphaMatte.release();
        
        return result;
    }
    
    // Enhanced segmentation using face detection
    private Mat createSegmentationMaskWithFaceDetection(Mat image) {
        // Detect faces in the image
        MatOfRect faceDetections = new MatOfRect();
        
        // Convert to grayscale for face detection
        Mat grayImage = new Mat();
        Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
        
        // Detect faces
        faceDetector.detectMultiScale(
            grayImage, 
            faceDetections,
            1.1,  // Scale factor
            5,    // Min neighbors 
            0,    // Flags
            new Size(30, 30), // Min size
            new Size()        // Max size (no limit)
        );
        
        // Create a segmentation mask
        Mat mask = new Mat(image.size(), CvType.CV_8UC1, new Scalar(0));
        
        // Process detected faces
        Rect[] faces = faceDetections.toArray();
        if (faces.length > 0) {
            // Use the largest face as primary focus
            Rect primaryFace = findLargestFace(faces);
            
            // Mark face area plus padding as definite foreground
            int padding = Math.max(primaryFace.width, primaryFace.height);
            Rect expandedFaceRect = expandRect(primaryFace, padding, image.size());
            
            // Fill the expanded face region
            Mat faceRegion = new Mat(mask, expandedFaceRect);
            faceRegion.setTo(new Scalar(255));
            faceRegion.release();
            
            // Now proceed with regular segmentation but prioritize the face region
            enhanceMaskWithColorSegmentation(image, mask);
            
            // Apply GrabCut using the face-enhanced mask
            return applyGrabCut(image, mask, expandedFaceRect);
        } else {
            // Fallback to standard segmentation
            grayImage.release();
            return createSegmentationMask(image);
        }
    }

    // Private assistive methods for implemented function
    private Mat createSegmentationMask(Mat image) {
        // Create a segmentation mask using traditional OpenCV methods
        
        // Convert to different color spaces
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
        
        // Create initial mask
        Mat mask = new Mat(image.size(), CvType.CV_8UC1, new Scalar(0));
        
        // Enhanced color segmentation
        enhanceMaskWithColorSegmentation(image, mask);
        
        // Detect edges for better segmentation
        Mat edges = new Mat();
        Mat grayImage = new Mat();
        Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
        Imgproc.Canny(grayImage, edges, 50, 150);
        
        // Dilate edges to connect segments
        Mat dilatedEdges = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.dilate(edges, dilatedEdges, kernel);
        
        // Add edges to mask
        Core.bitwise_or(mask, dilatedEdges, mask);
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_enhanced_mask.png", mask);
            Imgcodecs.imwrite("debug_edges.png", edges);
        }
        
        // Clean up resources
        edges.release();
        grayImage.release();
        dilatedEdges.release();
        
        // Apply GrabCut for better segmentation
        return applyGrabCut(image, mask, null);
    }
    
    private void enhanceMaskWithColorSegmentation(Mat image, Mat mask) {
        // Convert to different color spaces for better segmentation
        Mat hsv = new Mat();
        Mat ycrcb = new Mat();
        Mat lab = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
        Imgproc.cvtColor(image, ycrcb, Imgproc.COLOR_BGR2YCrCb);
        Imgproc.cvtColor(image, lab, Imgproc.COLOR_BGR2Lab);
        
        // Detect skin using multiple color spaces
        Mat skinMaskHSV = detectSkinHSV(hsv);
        Mat skinMaskYCrCb = detectSkinYCrCb(ycrcb);
        
        // Detect clothing in LAB color space
        Mat clothingMask = detectClothing(lab, image);
        
        // Combine skin masks
        Mat combinedSkinMask = new Mat();
        Core.bitwise_or(skinMaskHSV, skinMaskYCrCb, combinedSkinMask);
        
        // Combine skin and clothing
        Mat humanMask = new Mat();
        Core.bitwise_or(combinedSkinMask, clothingMask, humanMask);
        
        // Detect background (common in passport photos)
        Mat backgroundMask = detectBackgroundColors(hsv);
        
        // Get foreground (inverse of background)
        Mat foregroundMask = new Mat();
        Core.bitwise_not(backgroundMask, foregroundMask);
        
        // Combine all masks
        Mat enhancedMask = new Mat();
        Core.bitwise_or(humanMask, foregroundMask, enhancedMask);
        
        // Apply morphological operations to clean up
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.morphologyEx(enhancedMask, enhancedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Update the input mask
        Core.bitwise_or(mask, enhancedMask, mask);
        
        if (debugMode) {
            Imgcodecs.imwrite("debug_skin_hsv.png", skinMaskHSV);
            Imgcodecs.imwrite("debug_skin_ycrcb.png", skinMaskYCrCb);
            Imgcodecs.imwrite("debug_clothing.png", clothingMask);
            Imgcodecs.imwrite("debug_combined_skin.png", combinedSkinMask);
            Imgcodecs.imwrite("debug_human.png", humanMask);
            Imgcodecs.imwrite("debug_bg_mask.png", backgroundMask);
            Imgcodecs.imwrite("debug_fg_mask.png", foregroundMask);
            Imgcodecs.imwrite("debug_enhanced_combined.png", enhancedMask);
        }
        
        // Clean up resources
        hsv.release();
        ycrcb.release();
        lab.release();
        skinMaskHSV.release();
        skinMaskYCrCb.release();
        clothingMask.release();
        combinedSkinMask.release();
        humanMask.release();
        backgroundMask.release();
        foregroundMask.release();
        enhancedMask.release();
    }
    
    private Mat applyGrabCut(Mat image, Mat initialMask, Rect faceRect) {
        // Prepare GrabCut mask
        Mat grabCutMask = new Mat(image.size(), CvType.CV_8UC1, new Scalar(Imgproc.GC_PR_BGD));
        
        // Initialize counters for foreground and background samples
        int fgdCount = 0;
        int bgdCount = 0;
        
        // Set probable foreground from our masks with more aggressive thresholds
        for (int y = 0; y < initialMask.rows(); y++) {
            for (int x = 0; x < initialMask.cols(); x++) {
                double[] pixel = initialMask.get(y, x);
                if (pixel[0] > 180) {
                    grabCutMask.put(y, x, Imgproc.GC_FGD);
                    fgdCount++;
                } else if (pixel[0] > 120) {
                    grabCutMask.put(y, x, Imgproc.GC_PR_FGD);
                    fgdCount++;
                } else if (pixel[0] < 50) {
                    grabCutMask.put(y, x, Imgproc.GC_BGD);
                    bgdCount++;
                }
            }
        }
        
        // If we have a face rect, assume body extends below with wider area
        if (faceRect != null) {
            int bodyWidth = (int)(faceRect.width * 3.5);  // Increased body width
            int bodyHeight = (int)(faceRect.height * 4.5); // Increased body height
            
            // Calculate body rectangle - centered horizontally with face, extending down
            Rect bodyRect = new Rect(
                faceRect.x + faceRect.width/2 - bodyWidth/2, // Center with face
                faceRect.y + faceRect.height/2,             // Start from middle of face
                bodyWidth,
                bodyHeight
            );
            
            // Ensure body rect is within image bounds
            bodyRect.x = Math.max(0, bodyRect.x);
            bodyRect.y = Math.max(0, bodyRect.y);
            bodyRect.width = Math.min(image.width() - bodyRect.x, bodyRect.width);
            bodyRect.height = Math.min(image.height() - bodyRect.y, bodyRect.height);
            
            // Mark body area as probable foreground with higher confidence
            for (int y = bodyRect.y; y < bodyRect.y + bodyRect.height; y++) {
                for (int x = bodyRect.x; x < bodyRect.x + bodyRect.width; x++) {
                    if (y >= 0 && y < grabCutMask.rows() && x >= 0 && x < grabCutMask.cols()) {
                        // Check if it's not already definite foreground
                        if (grabCutMask.get(y, x)[0] != Imgproc.GC_FGD) {
                            grabCutMask.put(y, x, Imgproc.GC_PR_FGD);
                            fgdCount++;
                        }
                    }
                }
            }
            
            // Mark outer areas as definite background to help with segmentation
            int margin = 10;
            for (int y = 0; y < grabCutMask.rows(); y++) {
                for (int x = 0; x < margin; x++) {
                    grabCutMask.put(y, x, Imgproc.GC_BGD);
                    bgdCount++;
                }
                for (int x = grabCutMask.cols() - margin; x < grabCutMask.cols(); x++) {
                    grabCutMask.put(y, x, Imgproc.GC_BGD);
                    bgdCount++;
                }
            }
            
            // Use expanded rect for GrabCut with more padding
            Rect expandedRect = new Rect(
                Math.max(0, faceRect.x - faceRect.width * 2), 
                Math.max(0, faceRect.y - faceRect.height), 
                Math.min(image.width() - Math.max(0, faceRect.x - faceRect.width * 2), faceRect.width * 5),
                Math.min(image.height() - Math.max(0, faceRect.y - faceRect.height), faceRect.height * 6)
            );
            
            faceRect = expandedRect;
        } else {
            // If no face detected, use center of image with a larger estimate for human size
            int centerX = image.width() / 2;
            int centerY = image.height() / 2;
            int centerWidth = (int)(image.width() * 0.6);  // 60% of image width
            int centerHeight = (int)(image.height() * 0.8); // 80% of image height
            
            Rect centerRect = new Rect(
                centerX - centerWidth/2,
                centerY - centerHeight/3, // Position higher to account for head
                centerWidth,
                centerHeight
            );
            
            for (int y = centerRect.y; y < centerRect.y + centerRect.height; y++) {
                for (int x = centerRect.x; x < centerRect.x + centerRect.width; x++) {
                    if (y >= 0 && y < grabCutMask.rows() && x >= 0 && x < grabCutMask.cols()) {
                        if (grabCutMask.get(y, x)[0] != Imgproc.GC_FGD) {
                            grabCutMask.put(y, x, Imgproc.GC_PR_FGD);
                            fgdCount++;
                        }
                    }
                }
            }
        }
        
        // Ensure we have enough samples
        if (fgdCount < 100 || bgdCount < 100) {
            System.out.println("Warning: Not enough samples for GrabCut. FG: " + fgdCount + ", BG: " + bgdCount);
            // Add more background samples from the edges if needed
            if (bgdCount < 100) {
                int margin = 20;
                for (int y = 0; y < grabCutMask.rows() && bgdCount < 100; y++) {
                    for (int x = 0; x < margin; x++) {
                        if (grabCutMask.get(y, x)[0] != Imgproc.GC_BGD) {
                            grabCutMask.put(y, x, Imgproc.GC_BGD);
                            bgdCount++;
                        }
                    }
                }
            }
        }
        
        // Apply GrabCut with more iterations for better results
        Mat bgModel = new Mat();
        Mat fgModel = new Mat();
        
        try {
            // If we have a face rect, use it to init the algorithm
            if (faceRect != null) {
                Imgproc.grabCut(image, grabCutMask, faceRect, bgModel, fgModel, 8, Imgproc.GC_INIT_WITH_MASK);
            } else {
                Imgproc.grabCut(image, grabCutMask, new Rect(), bgModel, fgModel, 8, Imgproc.GC_INIT_WITH_MASK);
            }
        } catch (Exception e) {
            System.err.println("GrabCut error: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
        }
        
        // Get foreground mask with a more lenient approach
        Mat foreground = new Mat();
        Mat probForeground = new Mat();
        Core.compare(grabCutMask, new Scalar(Imgproc.GC_FGD), foreground, Core.CMP_EQ);
        Core.compare(grabCutMask, new Scalar(Imgproc.GC_PR_FGD), probForeground, Core.CMP_EQ);
        
        // Combine definite and probable foreground
        Mat finalMask = new Mat();
        Core.bitwise_or(foreground, probForeground, finalMask);
        
        // Convert to 8-bit
        finalMask.convertTo(finalMask, CvType.CV_8UC1, 255);
        
        // Apply closing to connect disconnected body parts
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(9, 9));
        Imgproc.morphologyEx(finalMask, finalMask, Imgproc.MORPH_CLOSE, kernel, new Point(-1, -1), 3);
        
        // Clean up resources
        grabCutMask.release();
        bgModel.release();
        fgModel.release();
        foreground.release();
        probForeground.release();
        
        return finalMask;
    }

    private Mat detectSkinHSV(Mat hsv) {
        // Detect skin tones in HSV
        Mat skinMask = new Mat(hsv.size(), CvType.CV_8UC1, new Scalar(0));
        
        // Skin tone ranges (improved ranges for better detection across skin types)
        Mat skinRegion1 = new Mat();
        Mat skinRegion2 = new Mat();
        Mat skinRegion3 = new Mat();
        
        // Lighter skin tones (0-50 in Hue)
        Core.inRange(hsv, new Scalar(0, 20, 70), new Scalar(50, 170, 255), skinRegion1);
        
        // Mid range skin tones (specific to certain ethnicities)
        Core.inRange(hsv, new Scalar(10, 50, 70), new Scalar(30, 200, 255), skinRegion2);
        
        // Darker skin tones
        Core.inRange(hsv, new Scalar(0, 10, 40), new Scalar(25, 150, 200), skinRegion3);
        
        // Combine ranges
        Core.bitwise_or(skinRegion1, skinRegion2, skinMask);
        Core.bitwise_or(skinMask, skinRegion3, skinMask);
        
        // Morphological operations to clean up
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.morphologyEx(skinMask, skinMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Clean up
        skinRegion1.release();
        skinRegion2.release();
        skinRegion3.release();
        
        return skinMask;
    }
    
    private Mat detectSkinYCrCb(Mat ycrcb) {
        // YCrCb color space is better for skin detection
        Mat skinMask = new Mat(ycrcb.size(), CvType.CV_8UC1, new Scalar(0));
        
        // Define skin tone range in YCrCb
        // These ranges cover most skin tones across ethnicities
        Core.inRange(ycrcb, new Scalar(0, 133, 77), new Scalar(255, 173, 127), skinMask);
        
        // Morphological operations to clean up
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.morphologyEx(skinMask, skinMask, Imgproc.MORPH_CLOSE, kernel);
        
        return skinMask;
    }

    private Mat detectBackgroundColors(Mat hsv) {
        // Detect common background colors in passport photos
        Mat blueMask = new Mat();
        Mat greenMask = new Mat();
        Mat whiteMask = new Mat();
        Mat grayMask = new Mat();
        Mat redMask = new Mat();
        
        // Blue background (common in passport photos)
        Core.inRange(hsv, new Scalar(100, 50, 50), new Scalar(140, 255, 255), blueMask);
        
        // Green background (common in chroma key)
        Core.inRange(hsv, new Scalar(40, 50, 50), new Scalar(80, 255, 255), greenMask);
        
        // White background (studio)
        Core.inRange(hsv, new Scalar(0, 0, 200), new Scalar(180, 30, 255), whiteMask);
        
        // Gray background (neutral)
        Core.inRange(hsv, new Scalar(0, 0, 100), new Scalar(180, 30, 180), grayMask);
        
        // Red background (sometimes used)
        Mat redMask1 = new Mat();
        Mat redMask2 = new Mat();
        Core.inRange(hsv, new Scalar(0, 50, 50), new Scalar(10, 255, 255), redMask1);
        Core.inRange(hsv, new Scalar(170, 50, 50), new Scalar(180, 255, 255), redMask2);
        Core.bitwise_or(redMask1, redMask2, redMask);
        
        // Combine backgrounds
        Mat backgroundMask = new Mat();
        Core.bitwise_or(blueMask, greenMask, backgroundMask);
        Core.bitwise_or(backgroundMask, whiteMask, backgroundMask);
        Core.bitwise_or(backgroundMask, grayMask, backgroundMask);
        Core.bitwise_or(backgroundMask, redMask, backgroundMask);
        
        // Clean up
        blueMask.release();
        greenMask.release();
        whiteMask.release();
        grayMask.release();
        redMask.release();
        redMask1.release();
        redMask2.release();
        
        return backgroundMask;
    }
    
    // Helper method to find the largest face rectangle
    private Rect findLargestFace(Rect[] faces) {
        if (faces.length == 0) return null;
        
        Rect largest = faces[0];
        for (Rect face : faces) {
            if (face.area() > largest.area()) {
                largest = face;
            }
        }
        return largest;
    }
    
    // Helper method to expand a rectangle with padding
    private Rect expandRect(Rect rect, int padding, Size imgSize) {
        int x = Math.max(0, rect.x - padding);
        int y = Math.max(0, rect.y - padding);
        int width = Math.min((int)imgSize.width - x, rect.width + padding * 2);
        int height = Math.min((int)imgSize.height - y, rect.height + padding * 2);
        
        return new Rect(x, y, width, height);
    }
    
    // Add a new method to detect clothing in LAB color space
    private Mat detectClothing(Mat lab, Mat originalImage) {
        // Create empty mask
        Mat clothingMask = new Mat(lab.size(), CvType.CV_8UC1, new Scalar(0));
        
        // Detect general clothing colors with improved ranges
        // Dark clothing (black, navy, etc.)
        Mat darkClothing = new Mat();
        Core.inRange(lab, new Scalar(0, 0, 0), new Scalar(80, 135, 135), darkClothing);
        
        // Light clothing (white, beige, etc.)
        Mat lightClothing = new Mat();
        Core.inRange(lab, new Scalar(130, 0, 0), new Scalar(255, 140, 140), lightClothing);
        
        // Blue clothing (specific for light blue shirts)
        Mat blueClothing = new Mat();
        Core.inRange(lab, new Scalar(100, 120, 130), new Scalar(200, 140, 150), blueClothing);
        
        // Colored clothing (using wider thresholds)
        Mat coloredClothing = new Mat();
        Core.inRange(lab, new Scalar(20, 110, 110), new Scalar(230, 250, 250), coloredClothing);
        
        // Combine all clothing detections
        Core.bitwise_or(darkClothing, lightClothing, clothingMask);
        Core.bitwise_or(clothingMask, blueClothing, clothingMask);
        Core.bitwise_or(clothingMask, coloredClothing, clothingMask);
        
        // Intensity-based detection (helps with complex textures)
        Mat grayImage = new Mat();
        Imgproc.cvtColor(originalImage, grayImage, Imgproc.COLOR_BGR2GRAY);
        
        // Use adaptive thresholding with improved parameters
        Mat adaptiveThresh = new Mat();
        Imgproc.adaptiveThreshold(grayImage, adaptiveThresh, 255, 
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
            Imgproc.THRESH_BINARY, 15, 5);
        
        // Combine with color-based detection
        Core.bitwise_or(clothingMask, adaptiveThresh, clothingMask);
        
        // Enhanced morphological operations
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(7, 7));
        
        // Close operation to fill gaps
        Imgproc.morphologyEx(clothingMask, clothingMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Dilate to ensure better coverage
        Imgproc.dilate(clothingMask, clothingMask, kernel, new Point(-1, -1), 2);
        
        // Clean up resources
        darkClothing.release();
        lightClothing.release();
        blueClothing.release();
        coloredClothing.release();
        grayImage.release();
        adaptiveThresh.release();
        
        return clothingMask;
    }
    
    @Override
    public void close() {
        // Clean up any resources
        if (faceDetector != null) {
            faceDetector.empty(); // Release native resources
        }
    }
}
