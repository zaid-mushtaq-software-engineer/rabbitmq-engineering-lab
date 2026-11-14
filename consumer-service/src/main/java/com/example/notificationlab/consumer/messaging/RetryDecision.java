package com.example.notificationlab.consumer.messaging;

public final class RetryDecision {

  private RetryDecision() {
  }

  public static RetryAction decide(int retryCount, int maxRetries, FailureClassification classification) {
    if (classification == FailureClassification.PERMANENT) {
      return RetryAction.DEAD_LETTER;
    }
    if (retryCount < maxRetries) {
      return RetryAction.RETRY;
    }
    return RetryAction.DEAD_LETTER;
  }
}