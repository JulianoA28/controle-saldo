package br.com.balancecontrol.item.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Edits descriptive fields only. {@code version} is the version the user saw: if the item
 * changed since then, the update is rejected with 409 and the current state (R5).
 */
public record UpdateItemRequest(
        @NotBlank @Size(max = 200) String description,
        @NotBlank @Size(max = 10) String unit,
        @NotNull Boolean active,
        @NotNull Long version) {
}
