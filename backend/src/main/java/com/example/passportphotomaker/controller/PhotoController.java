package com.example.passportphotomaker.controller;

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

            byte[] imageBytes = photoService.processImage(file, photoFormat, photoWidth, photoHeight, photoUnit);

            // Determine the media type dynamically based on the format
            MediaType mediaType = format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")
                    ? MediaType.IMAGE_JPEG
                    : MediaType.IMAGE_PNG;


            // If background image is provided, use it
            // if (backgroundImg != null && !backgroundImg.isEmpty()) {
            //     byte[] finalImageBytes = photoService.BackgroundChanger(
            //             transparentImageBytes, backgroundImg);
                
            //     return ResponseEntity.ok()
            //             .contentType(MediaType.IMAGE_PNG)
            //             .body(finalImageBytes);
            // } 

            if (true) {
                String backgroundImage = "beach.png";
                byte[] finalImageBytes = photoService.BackgroundChanger(
                        imageBytes, backgroundImage);
                
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(finalImageBytes);
            } 
            // If background color is provided but no image, use color
            // else if (backgroundColor != null && !backgroundColor.isEmpty()) {
            //     byte[] colorBackgroundImageBytes = photoService.BackgroundChanger(
            //             transparentImageBytes, backgroundColor);
                
            //     return ResponseEntity.ok()
            //             .contentType(MediaType.IMAGE_PNG)
            //             .body(colorBackgroundImageBytes);
            // }
            // If neither background image nor color is provided, return transparent image
            else {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(imageBytes);
            }
        } catch (IOException e) {
            System.err.println("Error processing photo: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}