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
     * Apply an image background to a transparent image with resize control
     * 
     * @param imageBytes The transparent image as byte array
     * @param backgroundFile The background image file
     * @param scale Scale factor for the background (1.0 = original size)
     * @param offsetX Horizontal offset for the background (percentage of image width, -1.0 to 1.0)
     * @param offsetY Vertical offset for the background (percentage of image height, -1.0 to 1.0)
     * @return The combined image as byte array
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, MultipartFile backgroundFile, 
                                        double scale, double offsetX, double offsetY) {
        try {
            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage foregroundImg = ImageIO.read(bais);

            // Read background image
            BufferedImage backgroundImg = ImageIO.read(backgroundFile.getInputStream());
            
            // Resize background with custom scale
            BufferedImage resizedBackground = resizeBackground(backgroundImg, foregroundImg.getWidth(), 
                                                              foregroundImg.getHeight(), scale, offsetX, offsetY);
            
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
     * Overloaded method for backward compatibility
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, MultipartFile backgroundFile) {
        // Default values: no scaling (1.0) and no offset (0.0, 0.0)
        return addBackgroundImg(imageBytes, backgroundFile, 1.0, 0.0, 0.0);
    }

    /**
     * Apply an image background using a file path with resize control
     * 
     * @param imageBytes The transparent image data
     * @param backgroundFilePath Path to the background image file
     * @param scale Scale factor for the background
     * @param offsetX Horizontal offset (-1.0 to 1.0)
     * @param offsetY Vertical offset (-1.0 to 1.0)
     * @return The combined image as byte array
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, String backgroundFilePath,
                                        double scale, double offsetX, double offsetY) {
        try {
            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage foregroundImg = ImageIO.read(bais);
    
            // Load background from file path
            BufferedImage backgroundImg = ImageIO.read(new File(backgroundFilePath));
            
            // Resize background with custom scale and offset
            BufferedImage resizedBackground = resizeBackground(backgroundImg, foregroundImg.getWidth(), 
                                                             foregroundImg.getHeight(), scale, offsetX, offsetY);
            
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
     * Overloaded method for backward compatibility
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, String backgroundFilePath) {
        // Default values: no scaling (1.0) and no offset (0.0, 0.0)
        return addBackgroundImg(imageBytes, backgroundFilePath, 1.0, 0.0, 0.0);
    }
    
    /**
     * Resize a background image to match target dimensions with scale and position control
     * 
     * @param backgroundImg The original background image
     * @param targetWidth The target width
     * @param targetHeight The target height
     * @param scale Scale factor (1.0 = fit exactly, >1.0 = zoom in, <1.0 = zoom out)
     * @param offsetX Horizontal offset (-1.0 to 1.0, where 0 is centered)
     * @param offsetY Vertical offset (-1.0 to 1.0, where 0 is centered)
     * @return The resized and positioned background image
     */
    private static BufferedImage resizeBackground(BufferedImage backgroundImg, int targetWidth, int targetHeight,
                                                double scale, double offsetX, double offsetY) {
        // Create a new BufferedImage with the target dimensions
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Set rendering hints for better quality
        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        
        // Adjust background size based on scale
        int tileWidth = (int)(backgroundImg.getWidth() * scale);
        int tileHeight = (int)(backgroundImg.getHeight() * scale);
        
        // Calculate offsets for tiling
        int xOffset = (int)(offsetX * tileWidth);
        int yOffset = (int)(offsetY * tileHeight);
        
        // Create a tiled pattern by drawing multiple copies
        for (int y = yOffset; y < targetHeight + tileHeight; y += tileHeight) {
            for (int x = xOffset; x < targetWidth + tileWidth; x += tileWidth) {
                g2d.drawImage(backgroundImg, x, y, tileWidth, tileHeight, null);
            }
        }
        
        g2d.dispose();
        return resizedImage;
    }
    
    /**
     * Overloaded method for backward compatibility
     */
    private static BufferedImage resizeBackground(BufferedImage backgroundImg, int targetWidth, int targetHeight) {
        return resizeBackground(backgroundImg, targetWidth, targetHeight, 1.0, 0.0, 0.0);
    }
}