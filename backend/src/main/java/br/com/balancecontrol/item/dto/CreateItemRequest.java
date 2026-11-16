package br.com.balancecontrol.item.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

public record CreateItemRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 200) String description,
        @NotBlank @Size(max = 10) String unit,
        @NotNull @PositiveOrZero Integer initialBalance) {
}
