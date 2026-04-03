package com.miniurl.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test utility for JWT authentication in integration tests.
 */
public class TestJwtUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Login and get JWT token for testing
     */
    public static String getJwtToken(MockMvc mockMvc, String username, String password) throws Exception {
        Map<String, String> loginRequest = Map.of(
            "username", username,
            "password", password
        );

        MvcResult result = mockMvc.perform(post("/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        // Response structure: {"success":true,"message":"...","data":{"token":"..."}}
        return objectMapper.readTree(responseContent).get("data").get("token").asText();
    }
}
