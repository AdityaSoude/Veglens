package com.example.veglens.controllers;


import com.example.veglens.service.ClassifierService;
import com.example.veglens.veglensapi.dto.ClassificationResponse;
import com.example.veglens.veglensapi.dto.IngredientsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;



@RestController
@RequiredArgsConstructor
public class ClassificationController {

    private final ClassifierService classifier;



    // POST /api/v1/classify/ingredients
    @PostMapping("/api/v1/classify/ingredients")
    public ClassificationResponse classifyIngredients(@Valid @RequestBody IngredientsRequest req) {
        return classifier.classifyTextPureVeg(req.text());
    }
}
