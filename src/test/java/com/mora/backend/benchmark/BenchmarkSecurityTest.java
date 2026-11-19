package com.mora.backend.benchmark;

import com.mora.backend.config.SecurityConfig;
import com.mora.backend.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@WebMvcTest(BenchmarkController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,JwtAuthenticationEntryPoint.class,RestAccessDeniedHandler.class})
class BenchmarkSecurityTest {
    MockMvc mvc;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy chain;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(chain).build(); }
    @MockitoBean BenchmarkService service;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean CustomUserDetailsService userDetails;
    @Test void anonymousRejected() throws Exception { mvc.perform(get("/api/admin/benchmarks/datasets")).andExpect(status().isUnauthorized()); }
    private void authenticate(String role) {
        var user=User.withUsername("fixture").password("unused").authorities(role).build();
        when(jwt.extractUserId("test-token")).thenReturn("fixture");
        when(userDetails.loadById("fixture")).thenReturn(user);
        when(jwt.isTokenValid("test-token",user)).thenReturn(true);
    }
    @Test void memberRejected() throws Exception {
        authenticate("ROLE_USER"); mvc.perform(get("/api/admin/benchmarks/datasets").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
    }
    @Test void adminAllowed() throws Exception {
        authenticate("ROLE_ADMIN"); mvc.perform(get("/api/admin/benchmarks/datasets").header("Authorization","Bearer test-token")).andExpect(status().isOk());
    }
    @Test void jsonDatasetRequestAndResponseAreCompatible() throws Exception {
        authenticate("ROLE_ADMIN");
        when(service.parse(any(String.class))).thenAnswer(call -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String)call.getArgument(0)));
        when(service.definition(1L)).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("name","fixture"));
        mvc.perform(get("/api/admin/benchmarks/datasets/1").header("Authorization","Bearer test-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.name").value("fixture"));
        mvc.perform(post("/api/admin/benchmarks/datasets").contentType("application/json").content("{\"name\":\"fixture\"}")
            .header("Authorization","Bearer test-token")).andExpect(status().isOk());
    }
}
