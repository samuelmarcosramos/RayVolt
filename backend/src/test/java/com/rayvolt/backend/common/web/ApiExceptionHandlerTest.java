package com.rayvolt.backend.common.web;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.common.exception.ConflictException;
import com.rayvolt.backend.common.exception.NotFoundException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.util.NoSuchElementException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiExceptionHandlerTest.OtherController.class)
@Import(ApiExceptionHandlerTest.OtherController.class)
@Tag("PT-32")
class ApiExceptionHandlerTest {
    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @CsvSource({"missing, 404", "conflict, 409", "bad-request, 400"})
    void applicationErrorsUseProblemDetailOutsideFleet(String error, int status) throws Exception {
        mvc.perform(get("/test/errors/" + error)).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"state-bug", "lookup-bug", "argument-bug"})
    void programmingErrorsReturn500WithoutLeakingDetails(String error) throws Exception {
        mvc.perform(get("/test/errors/" + error)).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void invalidPathVariableUsesProblemDetailOutsideFleet() throws Exception {
        mvc.perform(get("/test/ids/not-a-number")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"hubId\":\"invalid\"}", "null"})
    void unreadableBodyUsesProblemDetailOutsideFleet(String json) throws Exception {
        mvc.perform(post("/test/body").contentType("application/json").content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @RestController
    static class OtherController {
        @GetMapping("/test/errors/{error}")
        String error(@PathVariable String error) {
            throw switch (error) {
                case "missing" -> new NotFoundException("Synthetic missing resource");
                case "conflict" -> new ConflictException("Synthetic conflict");
                case "bad-request" -> new BadRequestException("Synthetic invalid input");
                case "state-bug" -> new IllegalStateException("Internal implementation detail");
                case "lookup-bug" -> new NoSuchElementException("Internal implementation detail");
                default -> new IllegalArgumentException("Internal implementation detail");
            };
        }

        @GetMapping("/test/ids/{id}")
        Long id(@PathVariable Long id) { return id; }

        @PostMapping("/test/body")
        Input body(@RequestBody Input input) { return input; }

        record Input(Long hubId) { }
    }
}
