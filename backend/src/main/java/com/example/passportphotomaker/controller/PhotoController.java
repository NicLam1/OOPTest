package com.example.passportphotomaker.controller;

import com.example.passportphotomaker.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // For development purposes
public class PhotoController {

    private final PhotoService photoService;

    @Autowired
    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/process-photo")
    public ResponseEntity<?> processPhoto(@RequestParam("image") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please upload an image");
            }

            byte[] processedImageBytes = photoService.passportifyImage(file);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(processedImageBytes);
                    
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process image: " + e.getMessage());
        }
    }
} 