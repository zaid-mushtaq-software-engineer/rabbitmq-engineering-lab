package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.consumer.simulation.PermanentDeliveryException;
import com.example.notificationlab.consumer.simulation.TemporaryDeliveryException;
import org.springframework.stereotype.Component;

@Component
public class FailureClassifier {

  public FailureClassification classify(Throwable throwable) {
    if (throwable instanceof PermanentDeliveryException || throwable instanceof MalformedMessageException) {
      return FailureClassification.PERMANENT;
    }
    if (throwable instanceof TemporaryDeliveryException) {
      return FailureClassification.TEMPORARY;
    }
    return FailureClassification.TEMPORARY;
  }
}