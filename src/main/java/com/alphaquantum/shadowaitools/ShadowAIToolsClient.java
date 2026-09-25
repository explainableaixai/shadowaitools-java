package com.alphaquantum.shadowaitools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Client for the Shadow AI Tools API. Instances are immutable and thread-safe. */
public final class ShadowAIToolsClient {
  private static final String DEFAULT_BASE_URL = "https://www.aitoolsblocklist.com/api";
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final String apiKey;
  private final String baseUrl;
  private final HttpClient http;
  private final Duration timeout;
  private final ObjectMapper json = new ObjectMapper();

  /**
   * Creates a client with the default endpoint and a 30 second request timeout.
   *
   * @param apiKey the API key; must not be blank
   */
  public ShadowAIToolsClient(String apiKey) {
    this(apiKey, DEFAULT_BASE_URL, defaultHttpClient(), Duration.ofSeconds(30));
  }

  private ShadowAIToolsClient(String apiKey, String baseUrl, HttpClient http, Duration timeout) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key is required");
    }
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.http = http;
    this.timeout = timeout;
  }

  /** @return a builder for custom endpoints, HTTP clients or timeouts */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Looks up one value (a domain) and returns the decoded JSON response.
   *
   * @param value the value to look up; must not be blank
   * @return the JSON response as a map
   * @throws ApiException if the service answers with an HTTP error status
   * @throws IOException on network errors, timeouts or invalid JSON
   * @throws InterruptedException if the calling thread is interrupted
   */
  public Map<String, Object> check(String value) throws IOException, InterruptedException {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Input is required");
    }
    URI uri = URI.create(baseUrl + "/check?domain=" + enc(value));
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json")
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new ApiException(response.statusCode(), response.body());
    }
    return json.readValue(response.body(), MAP);
  }

  /**
   * Reads a log or export file, extracts unique hostnames and checks each one in order.
   *
   * @param file path to a text or CSV file
   * @return one result per unique hostname, in the order first seen
   * @throws IOException if the file cannot be read or a request fails
   * @throws InterruptedException if the calling thread is interrupted
   */
  public List<Map<String, Object>> scan(String file) throws IOException, InterruptedException {
    Set<String> hosts = new LinkedHashSet<>();
    for (String line : Files.readAllLines(Path.of(file))) {
      for (String token : line.split("[\\s,;]+")) {
        if (token.isBlank()) {
          continue;
        }
        try {
          URI parsed = URI.create(token.contains("://") ? token : "https://" + token);
          String host = parsed.getHost();
          if (host != null && host.contains(".")) {
            hosts.add(host.toLowerCase(Locale.ROOT));
          }
        } catch (IllegalArgumentException ignored) {
          // not a URL or hostname; skip
        }
      }
    }
    List<Map<String, Object>> results = new ArrayList<>();
    for (String host : hosts) {
      results.add(check(host));
    }
    return results;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /** Builder for ShadowAIToolsClient. */
  public static final class Builder {
    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private HttpClient http;
    private Duration timeout = Duration.ofSeconds(30);

    private Builder() {}

    /** @param value the API key */
    public Builder apiKey(String value) {
      apiKey = value;
      return this;
    }

    /** @param value base URL of the API, for example a test server */
    public Builder baseUrl(String value) {
      baseUrl = value;
      return this;
    }

    /** @param value a shared or custom {@link HttpClient} */
    public Builder httpClient(HttpClient value) {
      http = value;
      return this;
    }

    /** @param value per-request timeout */
    public Builder timeout(Duration value) {
      timeout = value;
      return this;
    }

    /** @return a configured client */
    public ShadowAIToolsClient build() {
      return new ShadowAIToolsClient(apiKey, baseUrl, http == null ? defaultHttpClient() : http, timeout);
    }
  }
}
