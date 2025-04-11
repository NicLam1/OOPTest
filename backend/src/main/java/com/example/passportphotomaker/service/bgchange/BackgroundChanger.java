package com.example.passportphotomaker.service.bgchange;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class BackgroundChanger {
    // Debug flag - set to true to enable detailed logging
    private static final boolean DEBUG = true;

    /**
     * Apply a solid color background to a transparent image
     * 
     * @param imageBytes The image with transparent background as byte array
     * @param colorHex   The background color as hex string (e.g. "#FFFFFF")
     * @return The image with the new background color as byte array
     */
    public static byte[] addSolidColorBackground(byte[] imageBytes, String colorHex) {
        if (imageBytes == null) {
            System.err.println("Error: Image bytes cannot be null");
            return null;
        }

        // Convert hex string to Color
        Color color;
        try {
            color = Color.decode(colorHex);
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid color hex code: " + colorHex);
            color = Color.WHITE; // Default to white if invalid
        }

        ByteArrayInputStream bais = null;
        ByteArrayOutputStream baos = null;

        try {
            // Convert byte array to BufferedImage
            bais = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);

            if (image == null) {
                System.err.println("Error: Could not read image data - invalid format");
                return null;
            }

            // Create a new image with alpha support if needed
            BufferedImage transparentImage;
            if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
                transparentImage = new BufferedImage(
                        image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = transparentImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, null);
                g.dispose();
            } else {
                transparentImage = image;
            }

            // Create new image with solid background
            BufferedImage result = new BufferedImage(
                    transparentImage.getWidth(), transparentImage.getHeight(), BufferedImage.TYPE_INT_RGB);

            // Draw the background color
            Graphics2D g2d = result.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Fill with background color
            g2d.setColor(color);
            g2d.fillRect(0, 0, result.getWidth(), result.getHeight());

            // Draw the original image on top with SRC_OVER to properly handle alpha
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2d.drawImage(transparentImage, 0, 0, null);
            g2d.dispose();

            // Convert result to byte array
            baos = new ByteArrayOutputStream();
            ImageIO.write(result, "png", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            System.err.println("I/O error processing image: " + e.getMessage());
            return null;
        } finally {
            // Clean up resources
            try {
                if (bais != null)
                    bais.close();
                if (baos != null)
                    baos.close();
            } catch (IOException e) {
                System.err.println("Error closing streams: " + e.getMessage());
            }
        }
    }

    /**
     * Apply an image background to a transparent image
     * 
     * @param imageBytes     The transparent image as byte array
     * @param backgroundFile The background image file
     * @return The combined image as byte array
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, MultipartFile backgroundFile) {
        ByteArrayInputStream bais = null;
        ByteArrayOutputStream baos = null;

        try {
            // Convert byte array to BufferedImage for the foreground
            bais = new ByteArrayInputStream(imageBytes);
            BufferedImage foregroundImg = ImageIO.read(bais);
            bais.close();

            // Read background image
            BufferedImage backgroundImg = ImageIO.read(backgroundFile.getInputStream());

            // Create the final output image
            int width = foregroundImg.getWidth();
            int height = foregroundImg.getHeight();
            BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            // Create graphics for final composition
            Graphics2D g2d = finalImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Scale background to fit the image dimensions while maintaining aspect ratio
            double bgAspect = (double) backgroundImg.getWidth() / backgroundImg.getHeight();
            double imgAspect = (double) width / height;

            int bgWidth, bgHeight, bgX, bgY;

            if (bgAspect > imgAspect) {
                // Background is wider relative to its height than the image
                bgHeight = height;
                bgWidth = (int) (height * bgAspect);
                bgX = (width - bgWidth) / 2;
                bgY = 0;
            } else {
                // Background is taller relative to its width than the image
                bgWidth = width;
                bgHeight = (int) (width / bgAspect);
                bgX = 0;
                bgY = (height - bgHeight) / 2;
            }

            // Draw the background first
            g2d.drawImage(backgroundImg, bgX, bgY, bgWidth, bgHeight, null);

            // Now draw the foreground with alpha on top
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2d.drawImage(foregroundImg, 0, 0, null);

            g2d.dispose();

            // Convert result to byte array
            baos = new ByteArrayOutputStream();
            ImageIO.write(finalImage, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Error applying image background: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (bais != null)
                    bais.close();
                if (baos != null)
                    baos.close();
            } catch (IOException e) {
                System.err.println("Error closing streams: " + e.getMessage());
            }
        }
    }
}