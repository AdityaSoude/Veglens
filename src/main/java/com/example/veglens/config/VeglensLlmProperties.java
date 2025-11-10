package com.example.veglens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@Component
@Configuration
@ConfigurationProperties(prefix = "veglens.llm")
public class VeglensLlmProperties {
  private String baseUrl;
  private String model;
  private int timeoutMs = 60000;

  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public int getTimeoutMs() { return timeoutMs; }
  public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}
