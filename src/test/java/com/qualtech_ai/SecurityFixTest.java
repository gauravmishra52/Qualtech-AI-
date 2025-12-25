package com.qualtech_ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityFixTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testPublicEndpointDoesNotStackOverflow() throws Exception {
        // This request should not trigger StackOverflowError
        mockMvc.perform(get("/"))
                .andExpect(status().isOk()); // or isNotFound() depending on if index.html exists, but main point is no
                                             // crash
    }

    @Test
    public void testErrorEndpointDoesNotStackOverflow() throws Exception {
        // This request should not trigger StackOverflowError
        mockMvc.perform(get("/error"))
                .andExpect(status().isInternalServerError().or(status().isOk()).or(status().isNotFound()));
    }
}
