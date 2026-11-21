package com.ouroboros.security.externalauth.cas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for CAS ticket validation.
 * Uses java.net.HttpURLConnection (no external HTTP library dependency).
 */
public class CasHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(CasHttpClient.class);

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public CasHttpClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Validate a CAS ticket by calling the CAS server's validate endpoint.
     *
     * @param casBaseUrl   CAS server base URL (e.g. https://cas.example.com/cas)
     * @param validatePath validation endpoint path (e.g. /serviceValidate)
     * @param ticket       the CAS ticket to validate
     * @param serviceUrl   the service URL registered with CAS
     * @return raw XML response body
     * @throws IOException on connection or read failure
     */
    public String validateTicket(String casBaseUrl, String validatePath, String ticket, String serviceUrl)
            throws IOException {
        String encodedTicket = URLEncoder.encode(ticket, StandardCharsets.UTF_8.name());
        String encodedService = URLEncoder.encode(serviceUrl, StandardCharsets.UTF_8.name());
        String url = casBaseUrl + validatePath + "?ticket=" + encodedTicket + "&service=" + encodedService;

        logger.debug("CAS ticket validation request to: {}", casBaseUrl + validatePath);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setInstanceFollowRedirects(false);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("CAS validate endpoint returned HTTP " + responseCode);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
