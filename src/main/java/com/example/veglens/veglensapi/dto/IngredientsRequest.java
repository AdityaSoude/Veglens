package com.example.veglens.veglensapi.dto;

import jakarta.validation.constraints.NotBlank;

public record IngredientsRequest(

        @NotBlank String text) {}
