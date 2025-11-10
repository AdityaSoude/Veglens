package com.example.veglens;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloConfig {

    @GetMapping("/")
    public String hello() {
        return "VegLens backend is running 🚀";
    }
}
