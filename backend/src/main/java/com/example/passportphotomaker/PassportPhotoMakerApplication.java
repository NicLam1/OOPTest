package com.example.passportphotomaker;

import org.opencv.core.Core;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import javax.annotation.PostConstruct;

@SpringBootApplication
public class PassportPhotoMakerApplication {

    @PostConstruct
    public void init() {
        try {
            // Load OpenCV native library
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            System.out.println("OpenCV version: " + Core.VERSION);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV native library: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(PassportPhotoMakerApplication.class, args);
    }
} 