package com.example.passportphotomaker.service.bgchange;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import javax.imageio.ImageIO;

public class BackgroundChanger {
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
    
    // public static byte[] addBackgroundImg(byte[] imageBytes, MultipartFile background){
    //     try {
    //         // Convert byte array to BufferedImage
    //         ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
    //         BufferedImage image = ImageIO.read(bais);

    //         BufferedImage backgroundImg = ImageIO.read(background.getInputStream());
            
    //         // Draw the background color
    //         Graphics2D g2d = backgroundImg.createGraphics();

    //         // Draw the original image on top
    //         g2d.drawImage(image, 0, 0, null);
    //         g2d.dispose();

    //         // Convert result to byte array
    //         ByteArrayOutputStream baos = new ByteArrayOutputStream();
    //         ImageIO.write(backgroundImg, "png", baos);
    //         return baos.toByteArray();

    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         return null; // Return null or handle exceptions as appropriate
    //     }
    // }

    public static byte[] addBackgroundImg(byte[] imageBytes, String backgroundFilePath) {
        try {
            // Convert byte array to BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);
    
            // Load background from file path instead of MultipartFile
            BufferedImage backgroundImg = ImageIO.read(new File(backgroundFilePath));
            
            backgroundImg = backgroundImg.getSubimage(0, 0, image.getWidth(), image.getHeight());
            // Draw the foreground image on top of background
            Graphics2D g2d = backgroundImg.createGraphics();
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
    
            // Convert result to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(backgroundImg, "png", baos);
            
            // Optionally save for visual inspection during testing
            ImageIO.write(backgroundImg, "png", new File("test_output.png"));
            
            return baos.toByteArray();
    
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    






        // try {
        //     // Create output file
        //     OutputStream outStream = new FileOutputStream( "background.png" );
             
        //     // Load specified foreground and background JPEGs
        //     BufferedImage fgImage = ImageIO.read( new File( args[1] ) );
        //     BufferedImage bgImage = ImageIO.read( new File( args[0] ) );
             
        //     // Create a temporary BufferedImage for transparency.
        //     // Need to create it with TYPE_INT_ARGB so that it will have 
        //     // support for alpha transparency, which JPEGs do not have.
        //     BufferedImage tmpImage = new BufferedImage( fgImage.getWidth(), fgImage.getHeight(), BufferedImage.TYPE_INT_ARGB );
        //     Graphics gOut = tmpImage.createGraphics();
             
        //     // draw the foreground image on the temporary image
        //     gOut.drawImage( fgImage, 0, 0, null );
        //     gOut.dispose();
             
        //     // this block takes the temporary image,
        //     // grabs each pixel's red, green, blue, and alpha values,
        //     // messes with them, and writes it back out.
        //     int width = tmpImage.getWidth();
        //     int height = tmpImage.getHeight();
        //     int[] pixels = new int[ width * height ];
        //     pixels = tmpImage.getRGB( 0, 0, width, height, pixels, 0, width );
        //     for ( int i = 0; i < pixels.length; i++ ) {
        //         Color c = new Color( pixels[i] );
        //         int a = c.getAlpha();
        //         int r = c.getRed();
        //         int g = c.getGreen();
        //         int b = c.getBlue();
        //         // set alpha value to 65...
        //         // that means image is 65% transparent.
        //         c = new Color( r, g, b, 65 );
        //         pixels[i] = c.getRGB();
        //     }
        //     tmpImage.setRGB( 0, 0, width, height, pixels, 0, width );
             
        //     // paste the now transparent image onto the background.
        //     Graphics bgc = bgImage.createGraphics();
        //     bgc.drawImage( tmpImage, 10, 10, null );
        //     bgc.dispose();
             
        //     // Save the new composite image
        //     ImageIO.write( bgImage, "JPEG", outStream );
        //     outStream.close();
        // }
        // catch ( Exception x ) {
        //     x.printStackTrace();
        // }
    }


