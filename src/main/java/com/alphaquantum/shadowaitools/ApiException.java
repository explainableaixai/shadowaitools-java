package com.alphaquantum.shadowaitools;

/** Thrown when the API answers with an HTTP status of 400 or above. */
public final class ApiException extends RuntimeException {
  private final int statusCode;
  private final String body;

  /**
   * @param statusCode the HTTP status
   * @param body the raw response body
   */
  public ApiException(int statusCode, String body) {
    super("API request failed with status " + statusCode);
    this.statusCode = statusCode;
    this.body = body;
  }

  /** @return the HTTP status code */
  public int getStatusCode() {
    return statusCode;
  }

  /** @return the raw response body */
  public String getBody() {
    return body;
  }
}
