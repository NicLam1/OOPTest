package com.example.passportphotomaker.service.bgremove;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.engine.Engine;

public class DJLBackgroundRemover extends BackgroundRemover {
    // Segmentation model used
    private ZooModel<Image, NDArray> segmentationModel;
    private static final String DEFAULT_MODEL_URL = "djl://ai.djl.pytorch/u2net";
    private static final String PORTRAIT_MODEL_URL = "djl://ai.djl.pytorch/u2net_portrait";
    private boolean usingPortraitModel = false;

    // Constructors
    public DJLBackgroundRemover() throws ModelNotFoundException, MalformedModelException, IOException {
        loadSegmentationModel();
    }
    
    public DJLBackgroundRemover(boolean debugMode) throws ModelNotFoundException, MalformedModelException, IOException {
        super(debugMode);
        loadSegmentationModel();
    }

    // Main method to implement
    @Override
    public Mat removeBackground(File inputFile) throws IOException, TranslateException {
        // Load image using DJL
        Image image = ImageFactory.getInstance().fromFile(inputFile.toPath());
        
        // Get original dimensions for later resizing
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        
        System.out.println("======= STARTING BACKGROUND REMOVAL =======");
        System.out.println("Processing image: " + inputFile.getAbsolutePath());
        System.out.println("Image dimensions: " + originalWidth + "x" + originalHeight);
        
        // Check model information
        if (segmentationModel != null) {
            System.out.println("Model name: " + segmentationModel.getName());
            try {
                System.out.println("Model path: " + segmentationModel.getModelPath());
                
                // Extract and check file extension
                String modelPath = segmentationModel.getModelPath().toString();
                boolean modelIsOnnx = modelPath.toLowerCase().endsWith(".onnx");
                String modelType = modelIsOnnx ? "ONNX" : "PyTorch";
                System.out.println("Model file type based on extension: " + modelType);
                
                // Compare with property (if available)
                String onnxProperty = segmentationModel.getProperty("isOnnxModel", "unknown");
                System.out.println("Model isOnnxModel property: " + onnxProperty);
                
                if (modelIsOnnx && !"true".equals(onnxProperty)) {
                    System.out.println("WARNING: Model property doesn't match file extension!");
                }
            } catch (Exception e) {
                System.out.println("Error getting model details: " + e.getMessage());
            }
        } else {
            System.out.println("WARNING: Segmentation model is null!");
        }
        
        // Use model to predict segmentation mask directly
        try (Predictor<Image, NDArray> predictor = segmentationModel.newPredictor()) {
            // Get model type (ONNX or PyTorch)
            boolean isOnnx = segmentationModel.getName() != null && 
                           (segmentationModel.getName().toLowerCase().contains("onnx") ||
                            segmentationModel.getModelPath().toString().toLowerCase().endsWith(".onnx"));
            
            if (isOnnx) {
                System.out.println("Using ONNX model for prediction");
            } else {
                System.out.println("Using PyTorch model for prediction");
            }
            
            try {
                // Get the raw mask prediction
                System.out.println("Starting inference with model...");
                NDArray maskArray = predictor.predict(image);
                System.out.println("Prediction successful, mask shape: " + maskArray.getShape());
                
                // Convert NDArray mask to BufferedImage
                BufferedImage maskImage = createBufferedImageFromMask(maskArray, originalWidth, originalHeight);
                
                // Save debug output if needed
                if (debugMode) {
                    Path debugPath = Paths.get("debug_djl_mask.png");
                    ImageIO.write(maskImage, "png", debugPath.toFile());
                    System.out.println("Saved debug mask to: " + debugPath.toFile().getAbsolutePath());
                }
                
                // Convert mask to OpenCV Mat
                Mat cvMask = convertBufferedImageToMat(maskImage);
                
                // Load original image in OpenCV format
                Mat cvImage = Imgcodecs.imread(inputFile.getAbsolutePath());
                
                // Ensure mask is same size as image 
                if (cvMask.size().width != cvImage.size().width || cvMask.size().height != cvImage.size().height) {
                    System.out.println("Resizing mask from " + cvMask.size() + " to " + cvImage.size());
                    Imgproc.resize(cvMask, cvMask, cvImage.size());
                }
                
                // Post-process the mask to improve quality
                Mat enhancedMask = postProcessMask(cvImage, cvMask);
                
                // Create alpha matte for smooth edges
                Mat alphaMatte = createAlphaMatte(enhancedMask);
                
                // Create final transparent image
                Mat resultImage = createTransparentImage(cvImage, alphaMatte);
                
                // Save debug images if needed
                if (debugMode) {
                    Imgcodecs.imwrite("debug_cv_mask.png", cvMask);
                    Imgcodecs.imwrite("debug_enhanced_mask.png", enhancedMask);
                    Imgcodecs.imwrite("debug_alpha_matte.png", alphaMatte);
                    Imgcodecs.imwrite("debug_result.png", resultImage);
                }
                
                // Clean up
                cvImage.release();
                cvMask.release();
                enhancedMask.release();
                alphaMatte.release();
                
                System.out.println("Background removal completed successfully");
                return resultImage;
            } catch (Exception e) {
                System.err.println("ERROR during inference: " + e.getMessage());
                System.err.println("Error type: " + e.getClass().getName());
                if (e.getCause() != null) {
                    System.err.println("Caused by: " + e.getCause().getMessage());
                    System.err.println("Cause type: " + e.getCause().getClass().getName());
                    
                    // Check if this is an ONNX runtime error by class name
                    if (e.getCause().getClass().getName().contains("OrtException")) {
                        System.err.println("ONNX RUNTIME ERROR: This confirms we're using ONNX engine with possibly incompatible input format");
                    }
                }
                throw e;
            }
        } catch (Exception e) {
            System.err.println("Error in background removal: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            throw e;
        } finally {
            System.out.println("======= BACKGROUND REMOVAL PROCESS ENDED =======");
        }
    }
    
    // Post-process the mask to improve quality, especially for human subjects
    private Mat postProcessMask(Mat image, Mat initialMask) {
        // Apply threshold to make mask more decisive
        Mat thresholdedMask = new Mat();
        Imgproc.threshold(initialMask, thresholdedMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Create refined mask with morphological operations
        Mat refinedMask = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        
        // Close operation to fill gaps in the mask (especially useful for hair)
        Imgproc.morphologyEx(thresholdedMask, refinedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Further refinement with GrabCut if not using portrait model
        if (!usingPortraitModel) {
            refinedMask = refineMaskWithGrabCut(image, refinedMask);
        }
        
        // Apply edge-aware refinement
        Mat finalMask = refineMaskEdges(image, refinedMask);
        
        // Clean up
        thresholdedMask.release();
        refinedMask.release();
        
        return finalMask;
    }
    
    // Method to convert NDArray mask to BufferedImage
    private BufferedImage createBufferedImageFromMask(NDArray maskArray, int targetWidth, int targetHeight) {
        // Create grayscale image from mask
        BufferedImage maskImage = new BufferedImage(
            (int)maskArray.getShape().get(0), 
            (int)maskArray.getShape().get(1),
            BufferedImage.TYPE_BYTE_GRAY
        );
        
        // Get mask data (values between 0 and 1)
        float[] maskData = maskArray.toFloatArray();
        
        // Convert to byte array (0-255)
        byte[] byteData = new byte[maskData.length];
        for (int i = 0; i < maskData.length; i++) {
            byteData[i] = (byte)(maskData[i] * 255);
        }
        
        // Set data in the image
        maskImage.getRaster().setDataElements(0, 0, maskImage.getWidth(), maskImage.getHeight(), byteData);
        
        // Resize to target dimensions if needed
        if (maskImage.getWidth() != targetWidth || maskImage.getHeight() != targetHeight) {
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(maskImage, 0, 0, targetWidth, targetHeight, null);
            g.dispose();
            return resized;
        }
        
        return maskImage;
    }
    
    // Convert BufferedImage to OpenCV Mat
    private Mat convertBufferedImageToMat(BufferedImage image) {
        // Create OpenCV Mat with same dimensions
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);
        
        // Convert image data to byte array
        byte[] data = new byte[image.getWidth() * image.getHeight()];
        int[] rgb = image.getRaster().getPixels(0, 0, image.getWidth(), image.getHeight(), (int[])null);
        
        for (int i = 0; i < rgb.length; i++) {
            data[i] = (byte)rgb[i];
        }
        
        // Put data into Mat
        mat.put(0, 0, data);
        
        return mat;
    }
    
    // Enhance mask using GrabCut algorithm - specifically for human subjects
    private Mat refineMaskWithGrabCut(Mat image, Mat mask) {
        // Convert mask to GrabCut format (GC_BGD, GC_FGD, GC_PR_BGD, GC_PR_FGD)
        Mat grabCutMask = new Mat(image.size(), CvType.CV_8UC1, new Scalar(Imgproc.GC_PR_BGD));
        
        // Set foreground and background based on mask values
        for (int y = 0; y < mask.rows(); y++) {
            for (int x = 0; x < mask.cols(); x++) {
                double[] pixel = mask.get(y, x);
                if (pixel[0] > 200) {
                    grabCutMask.put(y, x, Imgproc.GC_FGD); // Definite foreground for high confidence areas
                } else if (pixel[0] > 100) {
                    grabCutMask.put(y, x, Imgproc.GC_PR_FGD); // Probable foreground
                } else if (pixel[0] < 30) {
                    grabCutMask.put(y, x, Imgproc.GC_BGD); // Definite background
                }
            }
        }
        
        // Apply GrabCut for better segmentation
        Mat bgModel = new Mat();
        Mat fgModel = new Mat();
        
        try {
            // For portrait photos, we can make assumptions about the subject position
            // Usually in the center of the frame
            int centerX = image.width() / 2;
            int centerY = image.height() / 2;
            int rectWidth = (int)(image.width() * 0.6); // 60% of image width
            int rectHeight = (int)(image.height() * 0.8); // 80% of image height
            
            Rect centerRect = new Rect(
                centerX - rectWidth/2,
                centerY - rectHeight/2,
                rectWidth,
                rectHeight
            );
            
            // Run GrabCut algorithm
            Imgproc.grabCut(image, grabCutMask, centerRect, bgModel, fgModel, 3, Imgproc.GC_INIT_WITH_MASK);
        } catch (Exception e) {
            System.err.println("GrabCut error: " + e.getMessage());
        }
        
        // Create final mask
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
        grabCutMask.release();
        bgModel.release();
        fgModel.release();
        foreground.release();
        probForeground.release();
        
        return finalMask;
    }

    // Close resources when no longer needed
    @Override
    public void close() {
        if (segmentationModel != null) {
            segmentationModel.close();
        }
    }

    // Custom translator for U2Net that returns the raw mask instead of DetectedObjects
    private static class U2NetTranslator implements Translator<Image, NDArray> {
        private final boolean isPortraitModel;
        private final boolean isOnnxModel;
        
        public U2NetTranslator(boolean isPortraitModel, boolean isOnnxModel) {
            this.isPortraitModel = isPortraitModel;
            this.isOnnxModel = isOnnxModel;
        }
        
        @Override
        public NDArray processOutput(TranslatorContext ctx, NDList list) {
            // Get the mask prediction from the model output
            NDArray maskArray = list.get(0);
            
            // The model returns values in range [0,1] where 1 represents foreground
            // We want to normalize and ensure it's a single-channel grayscale
            if (maskArray.getShape().dimension() > 2) {
                // If we have a batch or multiple channels, get the first one
                maskArray = maskArray.get(0).squeeze();
            }
            
            // Ensure the data is properly normalized between 0 and 1
            float min = maskArray.min().getFloat();
            float max = maskArray.max().getFloat();
            
            if (max > min) { // Avoid division by zero
                maskArray = maskArray.sub(min).div(max - min);
            }
            
            // For portrait model, enhance the contrast to better separate foreground from background
            if (isPortraitModel) {
                // Apply sigmoid-like contrast enhancement
                maskArray = maskArray.pow(0.5f); // Adjust power for different contrast levels
            }
            
            // Keep the array for return
            return maskArray;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            // Use the flag passed from constructor instead of trying to detect
            boolean isOnnx = this.isOnnxModel;
            
            // Double-check with model path for debugging purposes
            String modelPath = "";
            try {
                modelPath = ctx.getModel().getModelPath().toString();
                // Log the actual file path for debugging
                if (modelPath != null) {
                    System.out.println("FULL MODEL PATH: " + modelPath);
                    // Extract file extension for additional verification
                    int lastDotIndex = modelPath.lastIndexOf('.');
                    if (lastDotIndex > 0) {
                        String extension = modelPath.substring(lastDotIndex + 1).toLowerCase();
                        System.out.println("MODEL FILE EXTENSION: " + extension);
                        // Update based on actual file extension
                        boolean extensionIsOnnx = "onnx".equals(extension);
                        if (isOnnx != extensionIsOnnx) {
                            System.out.println("WARNING: Model flag (" + isOnnx + ") does not match file extension (" + extensionIsOnnx + ")");
                            // Trust the file extension
                            isOnnx = extensionIsOnnx;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error getting model path: " + e.getMessage());
            }
            
            String modelName = ctx.getModel().getName();
            System.out.println("MODEL INFO - Name: " + modelName + ", Is ONNX: " + isOnnx);
            
            // First resize the input image to the expected size
            int targetSize = isPortraitModel ? 512 : 320; // Portrait model works better with 512x512
            Image resizedImage = input.resize(targetSize, targetSize, true);
            
            // Convert to NDArray
            NDArray array = resizedImage.toNDArray(ctx.getNDManager());
            System.out.println("INITIAL ARRAY SHAPE: " + array.getShape());
            
            // Ensure image is RGB (3 channels)
            if (array.getShape().get(2) == 4) { // RGBA format
                array = array.get(":, :, :3"); // Keep only RGB channels
                System.out.println("AFTER CHANNEL SELECTION: " + array.getShape());
            }
            
            // Normalize to range [0, 1]
            array = array.div(255.0f);
            
            // ONNX models are sensitive to input format - be very explicit
            if (isOnnx) {
                System.out.println("PREPARING TENSOR FOR ONNX");
                
                // First make sure we have the right data type
                if (array.getDataType() != DataType.FLOAT32) {
                    array = array.toType(DataType.FLOAT32, false);
                    System.out.println("CONVERTED TO FLOAT32: " + array.getShape());
                }
                
                // Convert from HWC to CHW format (channels first)
                if (array.getShape().dimension() == 3 && array.getShape().get(2) == 3) {
                    // If in HWC format, convert to CHW
                    array = array.transpose(2, 0, 1);
                    System.out.println("AFTER TRANSPOSE: " + array.getShape());
                }
                
                // Add batch dimension if needed
                if (array.getShape().dimension() == 3) {
                    array = array.expandDims(0);
                    System.out.println("AFTER ADDING BATCH DIM: " + array.getShape());
                }
                
                // Ensure we have exactly 4 dimensions for ONNX
                Shape shape = array.getShape();
                System.out.println("SHAPE BEFORE FINAL CHECK: " + shape + ", dimension: " + shape.dimension());
                
                if (shape.dimension() > 4) {
                    System.out.println("WARNING: Input has too many dimensions (" + shape.dimension() + "), reshaping...");
                    // If we somehow have 5D tensor, reshape to 4D
                    array = array.reshape(1, 3, targetSize, targetSize);
                } else if (shape.dimension() != 4 || shape.get(0) != 1 || shape.get(1) != 3) {
                    System.out.println("WARNING: Unexpected shape, reshaping to standard ONNX format...");
                    array = array.reshape(1, 3, targetSize, targetSize);
                }
                
                System.out.println("FINAL ONNX INPUT SHAPE: " + array.getShape());
            } else {
                // For PyTorch models
                System.out.println("PREPARING TENSOR FOR PYTORCH");
                
                // Convert to CHW and add batch dimension
                array = array.transpose(2, 0, 1);
                System.out.println("AFTER TRANSPOSE: " + array.getShape());
                
                array = array.expandDims(0);
                System.out.println("FINAL PYTORCH INPUT SHAPE: " + array.getShape());
            }
            
            return new NDList(array);
        }
    }

    // Custom translator specifically for ONNX U2Net model
    private static class OnnxU2NetTranslator implements Translator<Image, NDArray> {
        private final int targetSize = 320; // U2Net's expected input size
        
        @Override
        public NDArray processOutput(TranslatorContext ctx, NDList list) {
            // Get the mask prediction from the model output
            NDArray maskArray = list.get(0);
            System.out.println("ONNX OUTPUT SHAPE: " + maskArray.getShape());
            
            // The model returns values in range [0,1] where 1 represents foreground
            // Extract the actual mask from any batch/channel dimensions
            if (maskArray.getShape().dimension() > 2) {
                // If we have a batch or multiple channels, get the first one
                maskArray = maskArray.get(0).squeeze();
                System.out.println("AFTER SQUEEZE: " + maskArray.getShape());
            }
            
            // Ensure the data is properly normalized between 0 and 1
            float min = maskArray.min().getFloat();
            float max = maskArray.max().getFloat();
            
            if (max > min) { // Avoid division by zero
                maskArray = maskArray.sub(min).div(max - min);
            }
            
            return maskArray;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            System.out.println("ONNX TRANSLATOR: Processing input for ONNX model");
            
            // Resize the input image to the expected size
            Image resizedImage = input.resize(targetSize, targetSize, true);
            System.out.println("Resized image to: " + targetSize + "x" + targetSize);
            
            // Convert to NDArray
            NDArray array = resizedImage.toNDArray(ctx.getNDManager());
            System.out.println("Initial array shape: " + array.getShape());
            
            // Ensure input has exactly 3 channels (RGB)
            if (array.getShape().get(2) == 4) { // RGBA format
                array = array.get(":, :, :3"); // Keep only RGB channels
            }
            System.out.println("After channel selection: " + array.getShape());
            
            // Normalize pixel values to [0,1]
            array = array.div(255.0f);
            
            // CRITICAL: Convert from HWC to CHW format (height, width, channels) -> (channels, height, width)
            array = array.transpose(2, 0, 1);
            System.out.println("After transpose (CHW format): " + array.getShape());
            
            // Add batch dimension to get NCHW format required by ONNX
            array = array.expandDims(0);
            System.out.println("Final ONNX tensor shape: " + array.getShape());
            
            // Verify we have exactly the shape (1, 3, 320, 320) and datatype float32
            if (array.getDataType() != DataType.FLOAT32) {
                array = array.toType(DataType.FLOAT32, false);
            }
            
            // Use reshape as a last resort to ensure exact dimensions
            Shape expected = new Shape(1, 3, targetSize, targetSize);
            if (!array.getShape().equals(expected)) {
                System.out.println("WARNING: Reshaping tensor to enforce exact dimensions");
                array = array.reshape(expected);
            }
            
            System.out.println("Final tensor shape: " + array.getShape() + ", dataType: " + array.getDataType());
            return new NDList(array);
        }
    }

    // Loading of model used with custom translator
    private void loadSegmentationModel() throws ModelNotFoundException, MalformedModelException, IOException {
        // Try to load from application directory first
        File modelsDir = new File("models");
        System.out.println("Absolute path to models directory: " + modelsDir.getAbsolutePath());
        
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
            System.out.println("Created models directory at: " + modelsDir.getAbsolutePath());
        } else {
            System.out.println("Models directory exists: " + modelsDir.exists());
            System.out.println("Models directory is readable: " + modelsDir.canRead());
            System.out.println("Models directory content: ");
            File[] files = modelsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    System.out.println(" - " + file.getName() + " (size: " + file.length() + " bytes)");
                }
            } else {
                System.out.println(" - Unable to list files in directory");
            }
        }
        
        // Check for model files with either .pt or .onnx extension
        File standardModelPt = new File(modelsDir, "u2net.pt");
        File standardModelOnnx = new File(modelsDir, "u2net.onnx");
        
        File modelToUse = null;
        String engineToUse = null;
        boolean isOnnxModel = false;
        
        // Check which file exists and use the appropriate one
        if (standardModelOnnx.exists()) {
            modelToUse = standardModelOnnx;
            engineToUse = "OnnxRuntime";
            isOnnxModel = true;
            System.out.println("Found ONNX model file: " + modelToUse.getAbsolutePath());
        } else if (standardModelPt.exists()) {
            modelToUse = standardModelPt;
            engineToUse = "PyTorch";
            isOnnxModel = false;
            System.out.println("Found PyTorch model file: " + modelToUse.getAbsolutePath());
        }
        
        if (modelToUse != null) {
            System.out.println("Using model file: " + modelToUse.getAbsolutePath());
            System.out.println("Model file size: " + modelToUse.length() + " bytes");
            System.out.println("Model file is readable: " + modelToUse.canRead());
            System.out.println("Model is ONNX format: " + isOnnxModel);
            
            try {
                System.out.println("Loading model from: " + modelToUse.getAbsolutePath() + " with engine: " + engineToUse);
                
                // Verify that the engine is available
                try {
                    System.out.println("Checking if engine '" + engineToUse + "' is available...");
                    ai.djl.engine.Engine engine = ai.djl.engine.Engine.getEngine(engineToUse);
                    System.out.println("Engine found: " + engine.getEngineName() + " version: " + engine.getVersion());
                    
                    // List available engines for debugging
                    System.out.println("Available engines:");
                    for (String engineName : Engine.getAllEngines()) {
                        Engine availEngine = Engine.getEngine(engineName);
                        System.out.println(" - " + availEngine.getEngineName() + " (version: " + availEngine.getVersion() + ")");
                    }
                } catch (Exception e) {
                    System.out.println("!!! ENGINE ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
                
                // Build the criteria
                Criteria.Builder<Image, NDArray> criteriaBuilder = Criteria.builder()
                    .optApplication(Application.CV.SEMANTIC_SEGMENTATION)
                    .setTypes(Image.class, NDArray.class)
                    .optModelPath(modelToUse.toPath())
                    .optEngine(engineToUse)
                    .optOption("mapLocation", "cpu"); // Force CPU inference
                
                // Use different translator based on model type
                if (isOnnxModel) {
                    criteriaBuilder.optTranslator(new OnnxU2NetTranslator());
                    System.out.println("Using dedicated ONNX translator");
                } else {
                    criteriaBuilder.optTranslator(new U2NetTranslator(false, false));
                    System.out.println("Using PyTorch translator");
                }
                
                System.out.println("Criteria built, loading model...");        
                segmentationModel = ModelZoo.loadModel(criteriaBuilder.build());
                
                // Store the model type for later use
                if (segmentationModel != null) {
                    // Adds a property to the model
                    segmentationModel.setProperty("isOnnxModel", String.valueOf(isOnnxModel));
                    System.out.println("Successfully loaded model from local file");
                    System.out.println("Model properties: isOnnxModel=" + segmentationModel.getProperty("isOnnxModel"));
                }
                return;
            } catch (Exception e) {
                System.out.println("!!! ERROR loading model: " + e.getMessage());
                if (debugMode) {
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println("No suitable model file found in: " + modelsDir.getAbsolutePath());
            
            // Try alternate location relative to working directory
            File currentDir = new File(".");
            System.out.println("Current working directory: " + currentDir.getAbsolutePath());
            
            // Check if file might be in a different location
            File[] possibleLocations = {
                new File("./models/u2net.onnx"),
                new File("./models/u2net.pt"),
                new File("../models/u2net.onnx"),
                new File("../models/u2net.pt"),
                new File("backend/models/u2net.onnx"),
                new File("backend/models/u2net.pt")
            };
            
            for (File location : possibleLocations) {
                System.out.println("Checking alternate location: " + location.getAbsolutePath() + " exists: " + location.exists());
                if (location.exists()) {
                    System.out.println("Found model at alternate location: " + location.getAbsolutePath());
                    String engine = location.getName().endsWith(".onnx") ? "OnnxRuntime" : "PyTorch";
                    try {
                        Criteria<Image, NDArray> criteria = 
                            Criteria.builder()
                                .optApplication(Application.CV.SEMANTIC_SEGMENTATION)
                                .setTypes(Image.class, NDArray.class)
                                .optModelPath(location.toPath())
                                .optTranslator(new U2NetTranslator(false, location.getName().endsWith(".onnx")))
                                .optEngine(engine)
                                .optOption("mapLocation", "cpu") // Force CPU for inference
                                .build();
                        
                        segmentationModel = ModelZoo.loadModel(criteria);
                        System.out.println("Successfully loaded model from alternate location");
                        return;
                    } catch (Exception e) {
                        System.out.println("Error loading from alternate location: " + e.getMessage());
                    }
                }
            }
        }
        
        // Model not found locally, show helpful message
        System.out.println("\n=========================================");
        System.out.println("U2NET MODEL NOT FOUND OR NOT LOADABLE - TROUBLESHOOTING:");
        System.out.println("1. Download from: https://github.com/danielgatis/rembg/raw/main/rembg/sessions/u2net.onnx");
        System.out.println("2. Save it to: " + new File(modelsDir, "u2net.onnx").getAbsolutePath() + " (keep the .onnx extension)");
        System.out.println("3. Make sure file is approximately 176MB in size");
        System.out.println("4. Make sure the required engines are available in your classpath");
        System.out.println("   Add: ai.djl.onnxruntime:onnxruntime-engine:0.32.0 for ONNX models");
        System.out.println("5. The application will fall back to OpenCV if model loading fails");
        System.out.println("=========================================\n");
        
        // If we reach here, we couldn't load any model
        throw new ModelNotFoundException("Could not find or load local model file. See console for troubleshooting details.");
    }
}
