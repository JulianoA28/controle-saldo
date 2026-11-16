package br.com.balancecontrol.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Checks the JSON contract and HTTP status of each error category. No database needed. */
@WebMvcTest(controllers = ApiExceptionHandlerTest.ThrowingController.class)
@Import(ApiExceptionHandlerTest.ThrowingController.class)
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void notFoundBecomes404() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Item not found."))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void conflictBecomes409WithDetails() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.details[0]").value("order 42"));
    }

    @Test
    void businessRuleBecomes422() throws Exception {
        mvc.perform(get("/test/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void invalidBodyBecomes400WithFields() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void malformedJsonBecomes400() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unexpectedBecomes500WithoutInternals() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error. Please try again later."));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("ITEM_NOT_FOUND", "Item not found.");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("ORDER_ALREADY_CANCELLED", "Order already cancelled.", List.of("order 42"));
        }

        @GetMapping("/test/rule")
        void rule() {
            throw new BusinessRuleException("INSUFFICIENT_BALANCE", "Insufficient balance.");
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    static class Payload {
        @NotBlank
        public String name;
    }
}
