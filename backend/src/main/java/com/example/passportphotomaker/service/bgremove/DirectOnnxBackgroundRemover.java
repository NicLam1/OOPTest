package com.example.passportphotomaker.service.bgremove;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

// Import Microsoft's ONNX Runtime
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.NodeInfo;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.MatOfPoint;

/**
 * Background remover implementation that uses Microsoft's ONNX Runtime directly
 * without any Deep Java Library (DJL) wrappers
 */
public class DirectOnnxBackgroundRemover extends BackgroundRemover {
    
    private OrtEnvironment env;
    private OrtSession session;
    private final int targetSize = 320; // U2Net's expected input size
    
    /**
     * Constructor for the Direct ONNX Runtime background remover
     */
    public DirectOnnxBackgroundRemover() throws IOException {
        this(false);
    }
    
    /**
     * Constructor with debug mode
     * @param debugMode Whether to output debug information
     */
    public DirectOnnxBackgroundRemover(boolean debugMode) throws IOException {
        super(debugMode);
        try {
            // Note: System properties are now set in PhotoService before creating this class
            // This is a compatibility check for multiple OS environments
            System.out.println("ONNX Runtime version check - java.vm.name: " + System.getProperty("java.vm.name"));
            System.out.println("ONNX Runtime version check - os.name: " + System.getProperty("os.name"));
            System.out.println("ONNX Runtime version check - os.arch: " + System.getProperty("os.arch"));
            
            initOnnxRuntime();
        } catch (UnsatisfiedLinkError e) {
            System.err.println("ONNX Runtime native library failed to load: " + e.getMessage());
            System.err.println("This could be due to missing dependencies like the Visual C++ Redistributable on Windows.");
            if (debugMode) {
                e.printStackTrace();
            }
            throw new IOException("Failed to initialize ONNX Runtime (native library error): " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("Error in DirectOnnxBackgroundRemover constructor: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            throw new IOException("Failed to initialize ONNX Runtime: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initialize ONNX Runtime and load the model
     */
    private void initOnnxRuntime() throws IOException {
        System.out.println("Initializing Direct ONNX Background Remover");
        
        // Create models directory if it doesn't exist
        File modelsDir = new File("models");
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
            System.out.println("Created models directory at: " + modelsDir.getAbsolutePath());
        }
        
        // Check for u2net.onnx
        File modelFile = new File(modelsDir, "u2net.onnx");
        if (!modelFile.exists()) {
            System.out.println("\n=========================================");
            System.out.println("U2NET MODEL NOT FOUND - PLEASE DOWNLOAD:");
            System.out.println("Download from: https://github.com/danielgatis/rembg/raw/main/rembg/sessions/u2net.onnx");
            System.out.println("Save it to: " + modelFile.getAbsolutePath());
            System.out.println("=========================================\n");
            throw new IOException("Model file not found at: " + modelFile.getAbsolutePath());
        }
        
        System.out.println("Found model file: " + modelFile.getAbsolutePath());
        System.out.println("Model file size: " + modelFile.length() + " bytes");
        
        try {
            // Create ONNX Runtime environment
            env = OrtEnvironment.getEnvironment();
            System.out.println("Created ONNX Runtime environment");
            
            // Configure session options
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            
            // Enable memory pattern optimization
            sessionOptions.setMemoryPatternOptimization(true);
            
            // CPU is the default execution provider
            System.out.println("Using default CPU execution provider");
            
            // Create session with the model file
            session = env.createSession(modelFile.getAbsolutePath(), sessionOptions);
            System.out.println("Created ONNX Runtime session with model");
            
            // Print model info
            System.out.println("Model inputs:");
            for (NodeInfo input : session.getInputInfo().values()) {
                System.out.println(" - " + input.getName() + ": " + input.getInfo());
            }
            
            System.out.println("Model outputs:");
            for (NodeInfo output : session.getOutputInfo().values()) {
                System.out.println(" - " + output.getName() + ": " + output.getInfo());
            }
            
            System.out.println("ONNX Runtime initialized successfully");
        } catch (OrtException e) {
            System.err.println("Error initializing ONNX Runtime: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            throw new IOException("Failed to initialize ONNX Runtime: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Mat removeBackground(File inputFile) throws IOException {
        System.out.println("======= STARTING DIRECT ONNX BACKGROUND REMOVAL =======");
        System.out.println("Processing image: " + inputFile.getAbsolutePath());
        
        // Load and preprocess the image
        BufferedImage originalImage = ImageIO.read(inputFile);
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        System.out.println("Image dimensions: " + originalWidth + "x" + originalHeight);
        
        try {
            // 1. Preprocess the image
            float[][][][] inputData = preprocessImage(originalImage, targetSize);
            
            // 2. Run inference
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);
            System.out.println("Created input tensor with shape: [1, 3, " + targetSize + ", " + targetSize + "]");
            
            // Run inference and get the output
            OrtSession.Result result = session.run(Collections.singletonMap("input.1", inputTensor));
            System.out.println("Inference completed successfully");
            
            // Extract the mask from the result (first output)
            OnnxValue outputValue = result.get(0);
            float[][][][] outputData = (float[][][][]) outputValue.getValue();
            
            // 3. Postprocess the mask
            BufferedImage maskImage = postprocessMask(outputData, originalWidth, originalHeight);
            
            // Save debug output if needed
            if (debugMode) {
                Path debugPath = Paths.get("debug_onnx_mask.png");
                ImageIO.write(maskImage, "png", debugPath.toFile());
                System.out.println("Saved debug mask to: " + debugPath.toFile().getAbsolutePath());
            }
            
            // 4. Convert mask to OpenCV Mat
            Mat cvMask = convertBufferedImageToMat(maskImage);
            
            // 5. Load original image in OpenCV format
            Mat cvImage = Imgcodecs.imread(inputFile.getAbsolutePath());
            
            // 6. Ensure mask is same size as image 
            if (cvMask.size().width != cvImage.size().width || cvMask.size().height != cvImage.size().height) {
                System.out.println("Resizing mask from " + cvMask.size() + " to " + cvImage.size());
                Imgproc.resize(cvMask, cvMask, cvImage.size());
            }
            
            // 7. Post-process the mask to improve quality
            Mat enhancedMask = postProcessMask(cvImage, cvMask);
            
            // 8. Create alpha matte for smooth edges
            Mat alphaMatte = createAlphaMatte(enhancedMask);
            
            // 9. Create final transparent image
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
            
            // Clean up ONNX resources
            inputTensor.close();
            result.close();
            
            System.out.println("Background removal completed successfully");
            return resultImage;
        } catch (OrtException e) {
            System.err.println("Error during ONNX inference: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            throw new IOException("ONNX inference failed: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("Error in background removal: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            throw new IOException("Error in background removal: " + e.getMessage(), e);
        } finally {
            System.out.println("======= BACKGROUND REMOVAL PROCESS ENDED =======");
        }
    }
    
    /**
     * Preprocess the image for ONNX inference
     * 
     * @param image The input image
     * @param targetSize The target size for the model
     * @return A 4D float array with shape [1, 3, height, width] normalized to [0, 1]
     */
    private float[][][][] preprocessImage(BufferedImage image, int targetSize) {
        // Resize image to target size
        BufferedImage resizedImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, targetSize, targetSize, null);
        graphics.dispose();
        
        System.out.println("Resized image to: " + targetSize + "x" + targetSize);
        
        // Create tensor with shape [1, 3, height, width]
        float[][][][] tensor = new float[1][3][targetSize][targetSize];
        
        // Fill tensor with normalized RGB values
        for (int y = 0; y < targetSize; y++) {
            for (int x = 0; x < targetSize; x++) {
                int rgb = resizedImage.getRGB(x, y);
                
                // Extract RGB values (0-255)
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Normalize to [0, 1] and store in NCHW format
                tensor[0][0][y][x] = r / 255.0f;
                tensor[0][1][y][x] = g / 255.0f;
                tensor[0][2][y][x] = b / 255.0f;
            }
        }
        
        System.out.println("Created input tensor with shape: [1, 3, " + targetSize + ", " + targetSize + "]");
        return tensor;
    }
    
    /**
     * Postprocess the mask from the model output
     * 
     * @param outputData The model output data
     * @param originalWidth The original image width
     * @param originalHeight The original image height
     * @return A binary mask as BufferedImage
     */
    private BufferedImage postprocessMask(float[][][][] outputData, int originalWidth, int originalHeight) {
        System.out.println("Output data shape: [" + outputData.length + ", " + 
                          outputData[0].length + ", " + 
                          outputData[0][0].length + ", " + 
                          outputData[0][0][0].length + "]");
        
        // Get mask from first output, first batch
        float[][] maskData = outputData[0][0];
        int maskHeight = maskData.length;
        int maskWidth = maskData[0].length;
        
        System.out.println("Extracted mask with dimensions: " + maskWidth + "x" + maskHeight);
        
        // Create grayscale image from mask
        BufferedImage maskImage = new BufferedImage(maskWidth, maskHeight, BufferedImage.TYPE_BYTE_GRAY);
        
        // Find min and max values for normalization
        float minVal = Float.MAX_VALUE;
        float maxVal = Float.MIN_VALUE;
        
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                float val = maskData[y][x];
                minVal = Math.min(minVal, val);
                maxVal = Math.max(maxVal, val);
            }
        }
        
        System.out.println("Mask value range: [" + minVal + ", " + maxVal + "]");
        
        // Normalize and convert to byte array
        byte[] pixels = new byte[maskWidth * maskHeight];
        
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                float normalizedValue = (maskData[y][x] - minVal) / (maxVal - minVal);
                pixels[y * maskWidth + x] = (byte)(normalizedValue * 255);
            }
        }
        
        // Set data in the image
        maskImage.getRaster().setDataElements(0, 0, maskWidth, maskHeight, pixels);
        
        // Resize to original dimensions if needed
        if (maskWidth != originalWidth || maskHeight != originalHeight) {
            BufferedImage resized = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(maskImage, 0, 0, originalWidth, originalHeight, null);
            g.dispose();
            return resized;
        }
        
        return maskImage;
    }
    
    /**
     * Convert BufferedImage to OpenCV Mat
     */
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
    
    /**
     * Post-process the mask to improve quality, especially for human subjects
     */
    private Mat postProcessMask(Mat image, Mat initialMask) {
        // Apply threshold to make mask more decisive
        Mat thresholdedMask = new Mat();
        Imgproc.threshold(initialMask, thresholdedMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Create refined mask with morphological operations
        Mat refinedMask = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        
        // Close operation to fill gaps in the mask (especially useful for hair)
        Imgproc.morphologyEx(thresholdedMask, refinedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Further refinement with GrabCut
        refinedMask = refineMaskWithGrabCut(image, refinedMask);
        
        // Apply edge-aware refinement
        Mat finalMask = refineMaskEdges(image, refinedMask);
        
        // Clean up
        thresholdedMask.release();
        refinedMask.release();
        
        return finalMask;
    }
    
    /**
     * Enhance mask using GrabCut algorithm - specifically for human subjects
     */
    private Mat refineMaskWithGrabCut(Mat image, Mat mask) {
        try {
            // Convert mask to GrabCut format (GC_BGD, GC_FGD, GC_PR_BGD, GC_PR_FGD)
            Mat grabCutMask = new Mat(image.size(), CvType.CV_8UC1, new Scalar(Imgproc.GC_PR_BGD));
            
            // Create counts to ensure we have both foreground and background samples
            int fgdCount = 0;
            int bgdCount = 0;
            
            // Set foreground and background based on mask values
            for (int y = 0; y < mask.rows(); y++) {
                for (int x = 0; x < mask.cols(); x++) {
                    double[] pixel = mask.get(y, x);
                    if (pixel[0] > 200) {
                        grabCutMask.put(y, x, Imgproc.GC_FGD); // Definite foreground for high confidence areas
                        fgdCount++;
                    } else if (pixel[0] > 100) {
                        grabCutMask.put(y, x, Imgproc.GC_PR_FGD); // Probable foreground
                        fgdCount++;
                    } else if (pixel[0] < 30) {
                        grabCutMask.put(y, x, Imgproc.GC_BGD); // Definite background
                        bgdCount++;
                    }
                }
            }
            
            // Check if we have both foreground and background samples
            if (fgdCount == 0 || bgdCount == 0) {
                System.out.println("GrabCut skipped: Not enough foreground (" + fgdCount + 
                                 ") or background (" + bgdCount + ") samples");
                
                // Force some samples if needed to avoid the error
                if (fgdCount == 0) {
                    // Add foreground samples in the center
                    int centerX = image.width() / 2;
                    int centerY = image.height() / 2;
                    int size = Math.min(50, Math.min(image.width(), image.height()) / 4);
                    
                    for (int y = centerY - size; y < centerY + size; y++) {
                        for (int x = centerX - size; x < centerX + size; x++) {
                            if (y >= 0 && y < image.height() && x >= 0 && x < image.width()) {
                                grabCutMask.put(y, x, Imgproc.GC_FGD);
                            }
                        }
                    }
                    System.out.println("Added forced foreground samples in center");
                }
                
                if (bgdCount == 0) {
                    // Add background samples around the edges
                    int border = 10;
                    
                    // Top and bottom rows
                    for (int x = 0; x < image.width(); x++) {
                        for (int y = 0; y < border; y++) {
                            if (y < image.height()) {
                                grabCutMask.put(y, x, Imgproc.GC_BGD);
                            }
                        }
                        for (int y = image.height() - border; y < image.height(); y++) {
                            if (y >= 0) {
                                grabCutMask.put(y, x, Imgproc.GC_BGD);
                            }
                        }
                    }
                    
                    // Left and right columns
                    for (int y = 0; y < image.height(); y++) {
                        for (int x = 0; x < border; x++) {
                            if (x < image.width()) {
                                grabCutMask.put(y, x, Imgproc.GC_BGD);
                            }
                        }
                        for (int x = image.width() - border; x < image.width(); x++) {
                            if (x >= 0) {
                                grabCutMask.put(y, x, Imgproc.GC_BGD);
                            }
                        }
                    }
                    System.out.println("Added forced background samples at edges");
                }
            }
            
            // Apply GrabCut for better segmentation
            Mat bgModel = new Mat();
            Mat fgModel = new Mat();
            
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
            System.out.println("GrabCut completed successfully");
            
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
        } catch (Exception e) {
            System.err.println("GrabCut error: " + e.getMessage());
            // If GrabCut fails, just return the original mask
            System.out.println("Returning original mask due to GrabCut failure");
            return mask.clone();
        }
    }
    
    /**
     * Apply hard edge refinement to the mask
     */
    @Override
    protected Mat refineMaskEdges(Mat image, Mat mask) {
        // Apply strong thresholding for hard binary mask
        Mat binaryMask = new Mat();
        Imgproc.threshold(mask, binaryMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Optional: Remove small noise and fill small holes
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        
        // First close to fill small holes
        Mat cleanedMask = new Mat();
        Imgproc.morphologyEx(binaryMask, cleanedMask, Imgproc.MORPH_CLOSE, kernel);
        
        // Then open to remove small isolated pixels
        Imgproc.morphologyEx(cleanedMask, cleanedMask, Imgproc.MORPH_OPEN, kernel);
        
        // Final thresholding to ensure binary result
        Mat finalMask = new Mat();
        Imgproc.threshold(cleanedMask, finalMask, 127, 255, Imgproc.THRESH_BINARY);
        
        // Clean up resources
        binaryMask.release();
        cleanedMask.release();
        
        return finalMask;
    }
    
    /**
     * Create binary alpha matte (no transparency)
     */
    @Override
    protected Mat createAlphaMatte(Mat mask) {
        // For hard edges, we just want a binary mask with no transparency
        Mat binaryMask = new Mat();
        Imgproc.threshold(mask, binaryMask, 127, 255, Imgproc.THRESH_BINARY);
        
        return binaryMask;
    }
    
    /**
     * Create transparent image with background removed
     */
    @Override
    protected Mat createTransparentImage(Mat image, Mat alphaMatte) {
        // Create 4-channel BGRA image
        Mat result = new Mat(image.size(), CvType.CV_8UC4);
        
        // Convert BGR to BGRA
        List<Mat> channels = new ArrayList<>(3);
        Core.split(image, channels);
        
        // Add alpha channel (alphaMatte)
        channels.add(alphaMatte);
        
        // Merge channels into result
        Core.merge(channels, result);
        
        // Release channel mats to avoid memory leaks
        for (int i = 0; i < channels.size() - 1; i++) {
            channels.get(i).release();
        }
        
        return result;
    }
    
    /**
     * Release ONNX Runtime resources
     */
    @Override
    public void close() {
        System.out.println("Closing DirectOnnxBackgroundRemover resources");
        
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            
            if (env != null) {
                env.close();
                env = null;
            }
        } catch (Exception e) {
            System.err.println("Error closing ONNX Runtime resources: " + e.getMessage());
        }
    }
} 