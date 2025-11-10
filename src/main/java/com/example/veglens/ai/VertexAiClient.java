package com.example.veglens.ai;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentRequest;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.PredictionServiceClient;
import com.google.cloud.vertexai.api.PredictionServiceSettings;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;

@Component
public class VertexAiClient implements AutoCloseable, Closeable {
  private final String projectId;
  private final String location;
  private final String modelName;      // taken as-is from application.yml
  private final String fullModelPath;  // projects/{p}/locations/{l}/publishers/google/models/{m}
  private final PredictionServiceClient svc;

  public VertexAiClient(Environment env) {
    this.projectId = env.getProperty("google.project-id");
    this.location  = env.getProperty("google.location", "us-central1");
    this.modelName = env.getProperty("vertex.model"); // do not default; force config
    if (projectId == null || projectId.isBlank()) {
      throw new IllegalStateException("google.project-id is not set");
    }
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("google.location is not set");
    }
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalStateException("vertex.model is not set");
    }

    String endpoint = location + "-aiplatform.googleapis.com:443";
    try {
      PredictionServiceSettings settings = PredictionServiceSettings.newBuilder()
          .setEndpoint(endpoint)
          .build();
      this.svc = PredictionServiceClient.create(settings);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create PredictionServiceClient for " + endpoint, e);
    }

    this.fullModelPath = String.format(
        "projects/%s/locations/%s/publishers/google/models/%s",
        projectId, location, modelName);
  }

  /** Uses exactly the configured model. No fallbacks. */
  public String generate(String prompt) throws IOException {
    try {
      GenerationConfig cfg = GenerationConfig.newBuilder()
          .setTemperature(0.0f)
          .setTopK(1)
          .build();

      Content user = Content.newBuilder()
          .setRole("user")
          .addParts(Part.newBuilder().setText(prompt).build())
          .build();

      GenerateContentRequest req = GenerateContentRequest.newBuilder()
          .setModel(fullModelPath)
          .addContents(user)
          .setGenerationConfig(cfg)
          .build();

      GenerateContentResponse resp = svc.generateContent(req);
      if (resp.getCandidatesCount() == 0) return "";
      return resp.getCandidates(0).getContent().getPartsList().stream()
          .filter(Part::hasText)
          .findFirst()
          .map(Part::getText)
          .orElse("");
    } catch (NotFoundException nf) {
      // Don’t suggest alternatives; report exactly what failed.
      throw new IOException("Configured Vertex model not found: " + fullModelPath
          + ". Verify the model name and region in application.yml.", nf);
    } catch (ApiException ae) {
      throw new IOException("Vertex generate failed for " + fullModelPath + ": " + ae.getMessage(), ae);
    }
  }

  @Override public void close() {
    try { if (svc != null) svc.close(); } catch (Exception ignore) {}
  }
}
