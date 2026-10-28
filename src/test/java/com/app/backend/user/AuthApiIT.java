package com.app.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.user.domain.User;
import com.app.backend.user.persistence.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
        properties = {"app.security.admin.email=Admin@Example.com", "app.security.admin.password=admin-pass-123"})
@AutoConfigureMockMvc
class AuthApiIT {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String registeredUserToken() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        return tokenFor(email, PASSWORD);
    }

    private static void expectProblem(ResultActions result, int status) throws Exception {
        result.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void registersLogsInAndReadsOwnProfile() throws Exception {
        String email = uniqueEmail();
        String mixedCaseEmail = email.toUpperCase();

        String registered = register(mixedCaseEmail, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(registered).get("id").asLong();

        JsonNode tokenBody = objectMapper.readTree(login(mixedCaseEmail, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String token = tokenBody.get("accessToken").asText();

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void storesPasswordAsBcryptHash() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD).andExpect(status().isCreated());

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        ResultActions duplicate = register(email.toUpperCase(), PASSWORD).andExpect(status().isConflict());

        expectProblem(duplicate, 409);
    }

    @Test
    void returnsSameUnauthorizedProblemForWrongPasswordAndUnknownEmail() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        ResultActions wrongPassword = login(email, "not-the-password").andExpect(status().isUnauthorized());
        ResultActions unknownEmail = login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized());

        expectProblem(wrongPassword, 401);
        expectProblem(unknownEmail, 401);
        assertThat(wrongPassword.andReturn().getResponse().getContentAsString()).contains("Invalid email or password");
        assertThat(unknownEmail.andReturn().getResponse().getContentAsString()).contains("Invalid email or password");
    }

    @Test
    void returnsUnauthorizedProblemWithoutToken() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        expectProblem(result, 401);
    }

    @Test
    void returnsUnauthorizedProblemForTamperedToken() throws Exception {
        String token = registeredUserToken();

        ResultActions result = mockMvc.perform(
                        get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());

        expectProblem(result, 401);
    }

    @Test
    void returnsUnauthorizedProblemForExpiredToken() throws Exception {
        Instant issuedAt = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("roles", List.of("ADMIN"))
                .build();
        String expired = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        ResultActions result = mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        expectProblem(result, 401);
    }

    @Test
    void forbidsUserTokenFromListingUsers() throws Exception {
        String token = registeredUserToken();

        ResultActions result = mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        expectProblem(result, 403);
    }

    @Test
    void forbidsUserTokenFromReadingAnotherUserById() throws Exception {
        String token = registeredUserToken();

        ResultActions result = mockMvc.perform(get("/api/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        expectProblem(result, 403);
    }

    @Test
    void allowsBootstrappedAdminToListAndReadUsers() throws Exception {
        String userEmail = uniqueEmail();
        String created = register(userEmail, PASSWORD)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long userId = objectMapper.readTree(created).get("id").asLong();
        String adminToken = tokenFor("admin@example.com", "admin-pass-123");

        mockMvc.perform(get("/api/users")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email == 'admin@example.com')].role")
                        .value("ADMIN"))
                .andExpect(jsonPath("$.content[?(@.email == '%s')].role".formatted(userEmail))
                        .value("USER"))
                .andExpect(jsonPath("$.content[*].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").isNumber());
        mockMvc.perform(get("/api/users/" + userId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userEmail));
        mockMvc.perform(get("/api/users/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void leavesPathsOutsideAuthAndUsersPublic() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isOk());
    }
}
