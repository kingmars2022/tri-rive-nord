package com.tririvenord.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Photo identification, switched off as on the free deployment.
 *
 * <p>It needs S3 and a Lambda the free deployment does not have, and residents
 * there were offered a photo button whose every upload failed. Off by
 * default: the page asks first and hides the button, and the endpoints refuse
 * plainly instead of failing somewhere in AWS.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class PhotoSwitchIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void offByDefault_andThePageCanAsk() throws Exception {
        mockMvc.perform(get("/api/photos/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void anUploadIsRefusedPlainly_notFailedInStorage() throws Exception {
        mockMvc.perform(post("/api/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":1000}"))
                .andExpect(status().isServiceUnavailable());
    }
}
