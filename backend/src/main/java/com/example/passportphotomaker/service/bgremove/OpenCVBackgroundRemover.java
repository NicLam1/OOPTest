package com.example.passportphotomaker.service.bgremove;

import java.io.File;
import java.io.IOException;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class OpenCVBackgroundRemover extends BackgroundRemover {
    // Constructors
    public OpenCVBackgroundRemover() {}
    public OpenCVBackgroundRemover(boolean debugMode) {
        super(debugMode);
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
        Mat mask = createSegmentationMask(originalImage);
        Mat refinedMask = refineMaskEdges(originalImage, mask);
        Mat alphaMatte = createAlphaMatte(refinedMask);
        Mat result = createTransparentImage(originalImage, alphaMatte);
        
        // Intermediary files for debugging purposes
        if (debugMode) {
            Imgcodecs.imwrite("debug_original.png", originalImage);
            Imgcodecs.imwrite("debug_initial_mask.png", mask);
            Imgcodecs.imwrite("debug_refined_mask.png", refinedMask);
            Imgcodecs.imwrite("debug_alpha_matte.png", alphaMatte);
        }
        
        // Clean up
        originalImage.release();
        mask.release();
        refinedMask.release();
        alphaMatte.release();
        
        return result;
    }

    // Private assistive methods for implemented function
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

}
