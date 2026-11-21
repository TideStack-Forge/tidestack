package com.ouroboros.security.externalauth.oauth2;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for OAuth2 protocol interactions (token exchange, userInfo, JWKS).
 */
public class OAuth2HttpClient {

  private final RestTemplate restTemplate;

  public OAuth2HttpClient(int connectTimeout, int readTimeout) {
    this(connectTimeout, readTimeout, () -> { });
  }

  public OAuth2HttpClient(int connectTimeout, int readTimeout, Runnable beforeRequest) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    this.restTemplate = new RestTemplate(factory);
    this.restTemplate.getInterceptors().add((request, body, execution) -> {
      beforeRequest.run();
      return execution.execute(request, body);
    });
  }

  /**
   * Exchange authorization code for tokens at the token endpoint.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> exchangeCode(String tokenUri, String code, String redirectUri,
                                            String codeVerifier, String clientId, String clientSecret) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("code", code);
    params.add("client_id", clientId);
    if (redirectUri != null) {
      params.add("redirect_uri", redirectUri);
    }
    if (clientSecret != null) {
      params.add("client_secret", clientSecret);
    }
    if (codeVerifier != null) {
      params.add("code_verifier", codeVerifier);
    }

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(params, headers);
    return restTemplate.postForObject(tokenUri, request, Map.class);
  }

  /**
   * Fetch user info from the userInfo endpoint using an access token.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> fetchUserInfo(String userInfoUri, String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessToken);

    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<Map> response = restTemplate.exchange(userInfoUri, HttpMethod.GET, request, Map.class);
    return response.getBody();
  }

  /**
   * Fetch JWKS (JSON Web Key Set) from the provider's JWKS endpoint.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> fetchJwks(String jwksUri) {
    return restTemplate.getForObject(jwksUri, Map.class);
  }
}
