package com.example.veglens.controllers;

import com.example.veglens.veglensapi.dto.ProductDtos.ProductDetailsResponse;
import com.example.veglens.policy.PolicyOptions;
import com.example.veglens.service.ProductDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

  private final ProductDetailsService svc;

  @GetMapping
  public ProductDetailsResponse get(
      @RequestParam(required = false) String barcode,
      @RequestParam(required = false, name = "q") String query,
      @RequestParam(defaultValue = "lenient") String policy,
      @RequestParam(defaultValue = "vegetarian") String diet,
      @RequestParam(defaultValue = "0", name = "forceBinary") int forceBinary
  ) {
    var opts = PolicyOptions.fromStrings(policy, diet, forceBinary != 0);
    return svc.fetchProduct(barcode, query, opts);
  }
}
