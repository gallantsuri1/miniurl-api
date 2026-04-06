package com.miniurl.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniurl.entity.Role;
import com.miniurl.entity.User;
import com.miniurl.entity.UserStatus;
import com.miniurl.repository.RoleRepository;
import com.miniurl.repository.UrlRepository;
import com.miniurl.repository.UserRepository;
import com.miniurl.util.TestJwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for URL CRUD operations.
 *
 * Tests cover:
 * - Creating shortened URLs
 * - Retrieving user's URLs
 * - Deleting URLs
 * - URL ownership validation
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("URL CRUD Integration Tests")
class UrlCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up database before each test - delete URLs first to avoid FK constraints
        try {
            urlRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }

        // Create USER role if not exists
        Role userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER", "Regular user")));

        // Create test user with unique username to avoid conflicts
        String uniqueId = String.valueOf(System.currentTimeMillis());
        User testUser = User.builder()
            .username("urltestuser" + uniqueId)
            .email("urltest" + uniqueId + "@example.com")
            .firstName("URL Test")
            .lastName("User")
            .password(passwordEncoder.encode("TestPass123!@#"))
            .role(userRole)
            .status(UserStatus.ACTIVE)
            .otpVerified(true)
            .build();

        userRepository.save(testUser);

        // Get JWT token for authenticated requests
        jwtToken = TestJwtUtil.getJwtToken(mockMvc, testUser.getUsername(), "TestPass123!@#");
    }

    @Test
    @DisplayName("Create URL with valid request should return shortened URL")
    void createUrlWithValidRequest_shouldReturnShortenedUrl() throws Exception {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("url", "https://www.example.com/very/long/url/path");

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.originalUrl").value("https://www.example.com/very/long/url/path"))
            .andExpect(jsonPath("$.data.shortCode").exists())
            .andExpect(jsonPath("$.data.shortUrl").exists())
            .andReturn();

        // Verify short code is not empty
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String shortCode = response.get("data").get("shortCode").asText();
        assertNotNull(shortCode);
        assertTrue(shortCode.length() > 0);
    }

    @Test
    @DisplayName("Create URL with custom alias should use the provided alias")
    void createUrlWithCustomAlias_shouldUseProvidedAlias() throws Exception {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("url", "https://www.example.com");
        request.put("alias", "mylink123");

        // Act & Assert
        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.shortCode").value("mylink123"))
            .andExpect(jsonPath("$.data.shortUrl").exists());
    }

    @Test
    @DisplayName("Create URL with short alias (less than 3 chars) should return 400")
    void createUrl_withShortAlias_shouldReturnBadRequest() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("url", "https://www.example.com");
        request.put("alias", "ab");

        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create URL with special chars in alias should return 400")
    void createUrl_withSpecialCharAlias_shouldReturnBadRequest() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("url", "https://www.example.com");
        request.put("alias", "my-link");

        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create URL with spaces in URL should return 400")
    void createUrl_withSpacesInUrl_shouldReturnBadRequest() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("url", "https://www.example.com/some url");

        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create URL with self-referencing domain should return 400")
    void createUrl_withSelfReferencingDomain_shouldReturnBadRequest() throws Exception {
        // The test profile sets app.base-url to http://localhost:8080
        // So shortening a localhost URL should be blocked
        Map<String, String> request = new HashMap<>();
        request.put("url", "http://localhost:8080/some/path");

        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Shortening URLs for this domain is not allowed"));
    }

    @Test
    @DisplayName("Create URL without authentication should return 401")
    void createUrlWithoutAuth_shouldReturnUnauthorized() throws Exception {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("url", "https://www.example.com");

        // Act & Assert
        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Create URL with invalid URL should return 400")
    void createUrlWithInvalidUrl_shouldReturnBadRequest() throws Exception {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("url", "not-a-valid-url");

        // Act & Assert
        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Get user URLs should return list of user's URLs")
    void getUserUrls_shouldReturnListOfUrls() throws Exception {
        // Arrange - Create a URL first
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("url", "https://www.test.com");

        mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isOk());

        // Act & Assert - Get all URLs (paginated response: $.data.content)
        mockMvc.perform(get("/api/urls")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Get user URLs without authentication should return 401")
    void getUserUrlsWithoutAuth_shouldReturnUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/urls"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Delete URL should remove the URL")
    void deleteUrl_shouldRemoveUrl() throws Exception {
        // Arrange - Create a URL first
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("url", "https://www.delete-test.com");

        MvcResult createResult = mockMvc.perform(post("/api/urls")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long urlId = createResponse.get("data").get("id").asLong();

        // Act & Assert - Delete the URL
        mockMvc.perform(delete("/api/urls/{id}", urlId)
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("URL deleted successfully"));

        // Verify URL is deleted by trying to get URLs again (paginated response)
        mockMvc.perform(get("/api/urls")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(0))
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("Delete URL without authentication should return 401")
    void deleteUrlWithoutAuth_shouldReturnUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/urls/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Delete non-existent URL should return 404")
    void deleteNonExistentUrl_shouldReturnNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/urls/99999")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Create multiple URLs and retrieve all")
    void createMultipleUrlsAndRetrieveAll() throws Exception {
        // Arrange - Create multiple URLs
        String[] urls = {
            "https://www.example1.com",
            "https://www.example2.com",
            "https://www.example3.com"
        };

        for (String url : urls) {
            Map<String, String> request = new HashMap<>();
            request.put("url", url);

            mockMvc.perform(post("/api/urls")
                    .with(csrf())
                    .header("Authorization", "Bearer " + jwtToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        // Act & Assert - Get all URLs (paginated response: $.data.content)
        MvcResult result = mockMvc.perform(get("/api/urls")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(3))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andReturn();

        // Verify all URLs are present
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = response.get("data").get("content");

        for (String expectedUrl : urls) {
            boolean found = false;
            for (JsonNode urlNode : content) {
                if (urlNode.get("originalUrl").asText().equals(expectedUrl)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "URL " + expectedUrl + " should be in the list");
        }
    }
}
