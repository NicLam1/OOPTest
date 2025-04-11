package com.example.passportphotomaker.controller;

import java.awt.Color;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.passportphotomaker.service.PhotoService;
import com.example.passportphotomaker.service.bgchange.BackgroundChanger;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // For development purposes
public class PhotoController {

    private final PhotoService photoService;

    @Autowired
    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/process-photo")
    public ResponseEntity<byte[]> processPhoto(
            @RequestParam("image") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "png") String format,
            @RequestParam(value = "backgroundColor", required = false) String backgroundColor,
            @RequestParam(value = "backgroundImg", required = false) MultipartFile backgroundImg,
            @RequestParam(value = "photoFormat", required = false) String photoFormat,
            @RequestParam(value = "photoWidth", required = false) Double photoWidth,
            @RequestParam(value = "photoHeight", required = false) Double photoHeight,
            @RequestParam(value = "photoUnit", required = false) String photoUnit)
            throws IOException {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Log the received parameters for debugging
            System.out.println("Received photo format: " + photoFormat);
            System.out.println("Received photo dimensions: " + photoWidth + "x" + photoHeight + " " + photoUnit);
            
            // Determine what we're doing - removing background or changing background
            boolean isBackgroundChangeRequest = backgroundColor != null || backgroundImg != null;
            MediaType mediaType = format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")
                    ? MediaType.IMAGE_JPEG 
                    : MediaType.IMAGE_PNG;
            
            if (isBackgroundChangeRequest) {
                // This is a background change request - handle appropriately
                byte[] processedImageBytes;
                
                if (backgroundColor != null && !backgroundColor.isEmpty()) {
                    // Apply color background
                    System.out.println("Applying color background: " + backgroundColor);
                    processedImageBytes = BackgroundChanger.addSolidColorBackground(file.getBytes(), backgroundColor);
                } else if (backgroundImg != null && !backgroundImg.isEmpty()) {
                    // Apply image background
                    System.out.println("Applying image background: " + backgroundImg.getOriginalFilename());
                    processedImageBytes = BackgroundChanger.addBackgroundImg(file.getBytes(), backgroundImg);
                } else {
                    // No background specified, just use the original image
                    processedImageBytes = file.getBytes();
                }
                
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .body(processedImageBytes);
            } else {
                // This is a regular background removal request - process as before
                byte[] imageBytes = photoService.processImage(file, photoFormat, photoWidth, photoHeight, photoUnit);
                
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=passport-photo." + format)
                        .body(imageBytes);
            }
        } catch (IOException e) {
            System.err.println("Error processing photo: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/adjust-photo")
    public ResponseEntity<byte[]> adjustPhoto(
        @RequestParam("image") MultipartFile file,
        @RequestParam("brightness") double brightness,
        @RequestParam("contrast") double contrast,
        @RequestParam("saturation") double saturation,
        @RequestParam(value = "format", defaultValue = "png") String format
    ) {
        System.out.println("==== /adjust-photo triggered ====");
        System.out.println("Brightness: " + brightness);
        System.out.println("Contrast: " + contrast);
        System.out.println("Saturation: " + saturation);
        System.out.println("File name: " + file.getOriginalFilename());
        System.out.println("File size: " + file.getSize());
        
    try {
        byte[] adjustedImage = photoService.adjustImage(file, brightness, contrast, saturation);

        MediaType mediaType = format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")
                ? MediaType.IMAGE_JPEG
                : MediaType.IMAGE_PNG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=adjusted-photo." + format)
                .body(adjustedImage);
    } catch (IOException e) {
        System.err.println(">>> Error adjusting image: " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(500).body(null);
    }
}


}