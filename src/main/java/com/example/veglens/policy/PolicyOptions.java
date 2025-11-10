// src/main/java/com/example/veglens/policy/PolicyOptions.java
package com.example.veglens.policy;

public record PolicyOptions(Mode policy, Diet diet, boolean forceBinary) {
  public enum Mode { LENIENT, STRICT }
  public enum Diet { VEGETARIAN, VEGAN }

  public static PolicyOptions fromStrings(String policy, String diet, boolean forceBinary) {
    Mode m = "strict".equalsIgnoreCase(policy) ? Mode.STRICT : Mode.LENIENT;
    Diet d = "vegan".equalsIgnoreCase(diet) ? Diet.VEGAN : Diet.VEGETARIAN;
    return new PolicyOptions(m, d, forceBinary);
  }

  public static PolicyOptions defaults() {
    return new PolicyOptions(Mode.LENIENT, Diet.VEGETARIAN, false);
  }
}
