package com.example.passportphotomaker.service.bgchange;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    // Debug flag - set to true to enable detailed logging
    private static final boolean DEBUG = true;

    /**
     * Apply a solid color background to a transparent image
     * 
     * @param imageBytes The image with transparent background as byte array
     * @param color      The background color
     * @return The image with the new background color as byte array
     */
    public static byte[] addSolidColorBackground(byte[] imageBytes, Color color) {
        if (imageBytes == null) {
            System.err.println("Error: Image bytes cannot be null");
            return null;
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

            // DEBUG logging here if needed
            System.out.println("Original image type: " + image.getType());
            System.out.println("Has alpha: " + image.getColorModel().hasAlpha());

            // Create a new image with alpha support if needed
            BufferedImage transparentImage;
            if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
                transparentImage = new BufferedImage(
                        image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = transparentImage.createGraphics();
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

            // Set rendering hints for better quality
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            // Fill with background color
            g2d.setColor(color);
            g2d.fillRect(0, 0, result.getWidth(), result.getHeight());

            // Use SRC_OVER to properly handle alpha
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

            // Draw the original image on top
            g2d.drawImage(transparentImage, 0, 0, null);
            g2d.dispose();

            // Convert result to byte array
            baos = new ByteArrayOutputStream();
            ImageIO.write(result, "png", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            System.err.println("I/O error processing image: " + e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            return null;
        } catch (NullPointerException e) {
            System.err.println("Null reference error: " + e.getMessage());
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
     * Apply a solid color background to a transparent image
     * 
     * @param imageBytes The image with transparent background as byte array
     * @param colorHex   The background color in hex format (e.g., "#ffffff")
     * @return The image with the new background color as byte array
     */
    public static byte[] addSolidColorBackground(byte[] imageBytes, String colorHex) {
        try {
            if (DEBUG) {
                System.out.println("=== Color Background Change Debug Info ===");
                System.out.println("Color hex: " + colorHex);
            }

            // Parse the hex color
            Color color = Color.decode(colorHex);

            if (DEBUG) {
                System.out.println("Decoded color: R=" + color.getRed() +
                        ", G=" + color.getGreen() +
                        ", B=" + color.getBlue());
                System.out.println("===================================");
            }

            return addSolidColorBackground(imageBytes, color);
        } catch (NumberFormatException e) {
            System.err.println("Invalid color format: " + e.getMessage());
            e.printStackTrace();
            // Fallback to white if color parsing fails
            return addSolidColorBackground(imageBytes, Color.WHITE);
        }
    }

    /**
     * Apply an image background to a transparent image with resize control
     * 
     * @param imageBytes     The transparent image as byte array
     * @param backgroundFile The background image file
     * @param scale          Scale factor for the background (1.0 = original size)
     * @param offsetX        Horizontal offset for the background (percentage of
     *                       image width, -1.0 to 1.0)
     * @param offsetY        Vertical offset for the background (percentage of image
     *                       height, -1.0 to 1.0)
     * @return The combined image as byte array
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, MultipartFile backgroundFile,
            double scale, double offsetX, double offsetY) {
        try {
            if (DEBUG) {
                System.out.println("=== Image Background Change Debug Info ===");
                System.out.println(
                        "Background file: " + (backgroundFile != null ? backgroundFile.getOriginalFilename() : "null"));
                System.out.println("Scale: " + scale + ", offsetX: " + offsetX + ", offsetY: " + offsetY);
            }

            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage foregroundImg = ImageIO.read(bais);

            if (DEBUG) {
                System.out.println("Foreground image loaded, type: " + getImageTypeString(foregroundImg.getType()));
                System.out.println("Has alpha: " + hasAlphaChannel(foregroundImg));
                System.out.println("Dimensions: " + foregroundImg.getWidth() + "x" + foregroundImg.getHeight());
            }

            // Ensure we're working with a compatible image type with alpha support
            BufferedImage transparentForeground = ensureARGBImage(foregroundImg);

            // Read background image
            BufferedImage backgroundImg = ImageIO.read(backgroundFile.getInputStream());

            if (DEBUG) {
                System.out.println("Background image loaded, type: " + getImageTypeString(backgroundImg.getType()));
                System.out.println(
                        "Background dimensions: " + backgroundImg.getWidth() + "x" + backgroundImg.getHeight());
            }

            // Resize background with custom scale
            BufferedImage backgroundCanvas = new BufferedImage(
                    transparentForeground.getWidth(),
                    transparentForeground.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            // Create graphics for background
            Graphics2D bgGraphics = backgroundCanvas.createGraphics();
            enableHighQualityRendering(bgGraphics);

            // Adjust background size based on scale
            int bgWidth = (int) (backgroundImg.getWidth() * scale);
            int bgHeight = (int) (backgroundImg.getHeight() * scale);

            // Calculate position based on offsets
            int bgX = (int) (offsetX * bgWidth);
            int bgY = (int) (offsetY * bgHeight);

            // Draw tiled background if needed to fill canvas
            drawTiledBackground(bgGraphics, backgroundImg, bgX, bgY,
                    bgWidth, bgHeight, backgroundCanvas.getWidth(), backgroundCanvas.getHeight());

            bgGraphics.dispose();

            // Final composition
            BufferedImage finalImage = new BufferedImage(
                    transparentForeground.getWidth(),
                    transparentForeground.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            Graphics2D finalGraphics = finalImage.createGraphics();
            enableHighQualityRendering(finalGraphics);

            // Draw background
            finalGraphics.drawImage(backgroundCanvas, 0, 0, null);

            // Draw foreground with alpha over background
            finalGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            finalGraphics.drawImage(transparentForeground, 0, 0, null);

            finalGraphics.dispose();

            if (DEBUG) {
                System.out.println("Final image created, type: " + getImageTypeString(finalImage.getType()));
                System.out.println("===================================");
            }

            // Convert result to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(finalImage, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Error applying image background: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Draw tiled background to ensure full coverage
     */
    private static void drawTiledBackground(Graphics2D g2d, BufferedImage backgroundImg,
            int startX, int startY, int tileWidth, int tileHeight,
            int canvasWidth, int canvasHeight) {
        // Calculate how many tiles we need
        int tilesX = (int) Math.ceil((double) canvasWidth / tileWidth) + 1;
        int tilesY = (int) Math.ceil((double) canvasHeight / tileHeight) + 1;

        // Adjust starting position to center or follow offset
        int xOffset = startX;
        int yOffset = startY;

        // Draw tiles to cover the entire canvas
        for (int y = -1; y < tilesY; y++) {
            for (int x = -1; x < tilesX; x++) {
                int posX = x * tileWidth + xOffset;
                int posY = y * tileHeight + yOffset;
                g2d.drawImage(backgroundImg, posX, posY, tileWidth, tileHeight, null);
            }
        }
    }

    /**
     * Ensures that the image is in ARGB format with proper alpha channel
     */
    private static BufferedImage ensureARGBImage(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
            return img; // Already in the right format
        }

        // Create a new image with alpha support
        BufferedImage argbImage = new BufferedImage(
                img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = argbImage.createGraphics();
        enableHighQualityRendering(g);

        // If there's no alpha originally, use full opacity
        if (!hasAlphaChannel(img)) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 1.0f));
        }

        g.drawImage(img, 0, 0, null);
        g.dispose();

        if (DEBUG) {
            System.out.println("Converted image from " + getImageTypeString(img.getType()) +
                    " to " + getImageTypeString(argbImage.getType()));
        }

        return argbImage;
    }

    /**
     * Enable high quality rendering settings
     */
    private static void enableHighQualityRendering(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * Check if an image has an alpha channel
     */
    private static boolean hasAlphaChannel(BufferedImage img) {
        return img.getColorModel().hasAlpha();
    }

    /**
     * Get a readable string for the image type
     */
    private static String getImageTypeString(int type) {
        switch (type) {
            case BufferedImage.TYPE_INT_ARGB:
                return "TYPE_INT_ARGB";
            case BufferedImage.TYPE_INT_RGB:
                return "TYPE_INT_RGB";
            case BufferedImage.TYPE_4BYTE_ABGR:
                return "TYPE_4BYTE_ABGR";
            case BufferedImage.TYPE_3BYTE_BGR:
                return "TYPE_3BYTE_BGR";
            case BufferedImage.TYPE_BYTE_GRAY:
                return "TYPE_BYTE_GRAY";
            case BufferedImage.TYPE_BYTE_BINARY:
                return "TYPE_BYTE_BINARY";
            case BufferedImage.TYPE_BYTE_INDEXED:
                return "TYPE_BYTE_INDEXED";
            case BufferedImage.TYPE_USHORT_565_RGB:
                return "TYPE_USHORT_565_RGB";
            case BufferedImage.TYPE_USHORT_555_RGB:
                return "TYPE_USHORT_555_RGB";
            case BufferedImage.TYPE_USHORT_GRAY:
                return "TYPE_USHORT_GRAY";
            case BufferedImage.TYPE_CUSTOM:
                return "TYPE_CUSTOM";
            default:
                return "Unknown Type: " + type;
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
     * @param imageBytes         The transparent image data
     * @param backgroundFilePath Path to the background image file
     * @param scale              Scale factor for the background
     * @param offsetX            Horizontal offset (-1.0 to 1.0)
     * @param offsetY            Vertical offset (-1.0 to 1.0)
     * @return The combined image as byte array
     */
    public static byte[] addBackgroundImg(byte[] imageBytes, String backgroundFilePath,
            double scale, double offsetX, double offsetY) {
        try {
            if (DEBUG) {
                System.out.println("=== File Path Background Change Debug Info ===");
                System.out.println("Background path: " + backgroundFilePath);
                System.out.println("Scale: " + scale + ", offsetX: " + offsetX + ", offsetY: " + offsetY);
            }

            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage foregroundImg = ImageIO.read(bais);

            if (DEBUG) {
                System.out.println("Foreground image loaded, type: " + getImageTypeString(foregroundImg.getType()));
                System.out.println("Has alpha: " + hasAlphaChannel(foregroundImg));
            }

            // Ensure proper alpha handling
            BufferedImage transparentForeground = ensureARGBImage(foregroundImg);

            // Load background from file path
            BufferedImage backgroundImg = ImageIO.read(new File(backgroundFilePath));

            if (DEBUG) {
                System.out.println("Background image loaded, type: " + getImageTypeString(backgroundImg.getType()));
            }

            // Create a properly sized background canvas
            BufferedImage backgroundCanvas = new BufferedImage(
                    transparentForeground.getWidth(),
                    transparentForeground.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            // Create graphics for background
            Graphics2D bgGraphics = backgroundCanvas.createGraphics();
            enableHighQualityRendering(bgGraphics);

            // Adjust background size based on scale
            int bgWidth = (int) (backgroundImg.getWidth() * scale);
            int bgHeight = (int) (backgroundImg.getHeight() * scale);

            // Calculate position based on offsets
            int bgX = (int) (offsetX * bgWidth);
            int bgY = (int) (offsetY * bgHeight);

            // Draw tiled background if needed to fill canvas
            drawTiledBackground(bgGraphics, backgroundImg, bgX, bgY,
                    bgWidth, bgHeight, backgroundCanvas.getWidth(), backgroundCanvas.getHeight());

            bgGraphics.dispose();

            // Final composition
            BufferedImage finalImage = new BufferedImage(
                    transparentForeground.getWidth(),
                    transparentForeground.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            Graphics2D finalGraphics = finalImage.createGraphics();
            enableHighQualityRendering(finalGraphics);

            // Draw background
            finalGraphics.drawImage(backgroundCanvas, 0, 0, null);

            // Draw foreground with alpha over background
            finalGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            finalGraphics.drawImage(transparentForeground, 0, 0, null);

            finalGraphics.dispose();

            if (DEBUG) {
                System.out.println("Final image created, type: " + getImageTypeString(finalImage.getType()));
                System.out.println("=======================================");
            }

            // Convert result to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(finalImage, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Error applying background from file: " + e.getMessage());
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
}