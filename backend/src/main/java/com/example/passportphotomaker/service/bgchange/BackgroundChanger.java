package com.example.passportphotomaker.service.bgchange;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class BackgroundChanger {
    /**
     * Apply a solid color background to a transparent image
     * 
     * @param imageBytes The image with transparent background as byte array
     * @param color The background color
     * @return The image with the new background color as byte array
     */
    public static byte[] addSolidColorBackground(byte[] imageBytes, Color color) {
        try {
            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);
            
            // Create new image with solid background
            BufferedImage background = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB
            );

            // Draw the background color
            Graphics2D g2d = background.createGraphics();
            g2d.setColor(color); // Use the color parameter
            g2d.fillRect(0, 0, background.getWidth(), background.getHeight());

            // Draw the original image on top
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();

            // Convert result to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(background, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null or handle exceptions as appropriate
        }
    }
    
    /**
     * Apply a solid color background to a transparent image
     * 
     * @param imageBytes The image with transparent background as byte array
     * @param colorHex The background color in hex format (e.g., "#ffffff")
     * @return The image with the new background color as byte array
     */
    public static byte[] addSolidColorBackground(byte[] imageBytes, String colorHex) {
        try {
            // Parse the hex color
            Color color = Color.decode(colorHex);
            return addSolidColorBackground(imageBytes, color);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            // Fallback to white if color parsing fails
            return addSolidColorBackground(imageBytes, Color.WHITE);
        }
    }
    
    /**
     * Apply an image background to a transparent image
     * 
     * @param imageBytes The transparent image as byte array
     * @param backgroundFile The background image file
     * @return The combined image as byte array
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, MultipartFile backgroundFile) {
        try {
            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage foregroundImg = ImageIO.read(bais);

            // Read background image
            BufferedImage backgroundImg = ImageIO.read(backgroundFile.getInputStream());
            
            // Resize background to match foreground dimensions
            BufferedImage resizedBackground = resizeBackground(backgroundImg, foregroundImg.getWidth(), foregroundImg.getHeight());
            
            // Draw the foreground on top of background
            Graphics2D g2d = resizedBackground.createGraphics();
            g2d.drawImage(foregroundImg, 0, 0, null);
            g2d.dispose();

            // Convert result to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedBackground, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Apply an image background using a file path
     * 
     * @param imageBytes The transparent image data
     * @param backgroundFilePath Path to the background image file
     * @return The combined image as byte array
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, String backgroundFilePath) {
        try {
            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage foregroundImg = ImageIO.read(bais);
    
            // Load background from file path
            BufferedImage backgroundImg = ImageIO.read(new File(backgroundFilePath));
            
            // Resize background to match the foreground dimensions
            BufferedImage resizedBackground = resizeBackground(backgroundImg, foregroundImg.getWidth(), foregroundImg.getHeight());
            
            // Draw the foreground on top of the background
            Graphics2D g2d = resizedBackground.createGraphics();
            g2d.drawImage(foregroundImg, 0, 0, null);
            g2d.dispose();
    
            // Convert result to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedBackground, "png", baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Resize a background image to match target dimensions
     * 
     * @param backgroundImg The original background image
     * @param targetWidth The target width
     * @param targetHeight The target height
     * @return The resized background image
     */
    private static BufferedImage resizeBackground(BufferedImage backgroundImg, int targetWidth, int targetHeight) {
        // If dimensions already match, no need to resize
        if (backgroundImg.getWidth() == targetWidth && backgroundImg.getHeight() == targetHeight) {
            return backgroundImg;
        }
        
        // Create a new BufferedImage with the target dimensions
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        
        // Get the graphics context for the new image
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Set rendering hints for better quality
        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        
        // Calculate dimensions to maintain aspect ratio and cover the entire area
        double originalRatio = (double) backgroundImg.getWidth() / backgroundImg.getHeight();
        double targetRatio = (double) targetWidth / targetHeight;
        
        int drawX = 0;
        int drawY = 0;
        int drawWidth = targetWidth;
        int drawHeight = targetHeight;
        
        // Adjust dimensions to maintain aspect ratio while covering the target area
        if (originalRatio > targetRatio) {
            // Original image is wider, so scale by height
            drawWidth = (int) (targetHeight * originalRatio);
            drawX = (targetWidth - drawWidth) / 2;
        } else {
            // Original image is taller, so scale by width
            drawHeight = (int) (targetWidth / originalRatio);
            drawY = (targetHeight - drawHeight) / 2;
        }
        
        // Draw the scaled image
        g2d.drawImage(backgroundImg, drawX, drawY, drawWidth, drawHeight, null);
        g2d.dispose();
        
        return resizedImage;
    }
}