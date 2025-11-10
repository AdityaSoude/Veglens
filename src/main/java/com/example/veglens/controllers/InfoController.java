// src/main/java/com/example/veglens/controllers/InfoController.java
package com.example.veglens.controllers;

import com.example.veglens.policy.PolicyOptions;
import com.example.veglens.service.InfoService;
import com.example.veglens.veglensapi.dto.InfoDtos.InfoRequest;
import com.example.veglens.veglensapi.dto.InfoDtos.InfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/info")
@RequiredArgsConstructor
public class InfoController {

  private final InfoService service;

  @PostMapping(produces = "application/json")
public InfoResponse fetchInfo(@RequestBody InfoRequest req,
                              @RequestParam(defaultValue="lenient") String policy,
                              @RequestParam(defaultValue="vegetarian") String diet,
                              @RequestParam(defaultValue="0", name="forceBinary") int forceBinary) {
  var opts = PolicyOptions.fromStrings(policy, diet, forceBinary != 0);
  return service.lookup(req, opts);
  }
}
