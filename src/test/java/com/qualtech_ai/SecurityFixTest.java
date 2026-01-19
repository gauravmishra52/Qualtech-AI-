package com.qualtech_ai;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityFixTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testPublicEndpointDoesNotStackOverflow() throws Exception {

        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    public void testErrorEndpointDoesNotStackOverflow() throws Exception {

        mockMvc.perform(get("/error"))
                .andExpect(status().is(anyOf(is(500), is(200), is(404))));
    }
}
