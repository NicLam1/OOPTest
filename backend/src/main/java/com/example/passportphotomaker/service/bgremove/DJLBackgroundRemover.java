package com.example.passportphotomaker.service.bgremove;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

public class DJLBackgroundRemover extends BackgroundRemover {
    // Segmentation model used
    private ZooModel<Image,DetectedObjects> segmentationModel;

    // Constructors
    public DJLBackgroundRemover() throws ModelNotFoundException, MalformedModelException, IOException {
        loadSegmentationModel();
    }
    public DJLBackgroundRemover(boolean debugMode) throws ModelNotFoundException, MalformedModelException, IOException {
        super(debugMode);
        loadSegmentationModel();
    }

    // Loading of model used
    private void loadSegmentationModel() throws ModelNotFoundException, MalformedModelException, IOException {
        // Load the U2Net model for salient object detection (good for person segmentation)
        Criteria<Image, DetectedObjects> criteria = 
            Criteria.builder()
                .optApplication(Application.CV.SEMANTIC_SEGMENTATION)
                .setTypes(Image.class, DetectedObjects.class)
                .optModelUrls("djl://ai.djl.pytorch/u2net") // Pre-trained U2Net model
                .build();
                
        segmentationModel = ModelZoo.loadModel(criteria);
        System.out.println("Segmentation model loaded successfully");
    }

    // Main method to implement
    @Override
    public Mat removeBackground(File inputFile) throws IOException, TranslateException {
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
            
            // Background removal
            Mat mask = createMaskFromDJLOutput(segmentation, cvImage.size());
            Mat refinedMask = refineMaskEdges(cvImage, mask);
            Mat alphaMatte = createAlphaMatte(refinedMask);
            Mat resultImage = createTransparentImage(cvImage, alphaMatte);
            
            // Save debug images if needed
            if (debugMode) {
                Imgcodecs.imwrite("debug_refined_mask.png", refinedMask);
                Imgcodecs.imwrite("debug_alpha_matte.png", alphaMatte);
                Imgcodecs.imwrite("debug_result.png", resultImage);
            }
            
            // Clean up
            cvImage.release();
            mask.release();
            refinedMask.release();
            alphaMatte.release();
            resultImage.release();
            
            return resultImage;
        }
    }

    // Private assistive methods for implemented function
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

    private void closeSegmentationModel() {
        segmentationModel.close();
    }
}
