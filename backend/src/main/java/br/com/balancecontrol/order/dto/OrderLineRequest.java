package br.com.balancecontrol.order.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public record OrderLineRequest(
        @NotBlank @Size(max = 20) String itemCode,
        @NotNull @Positive Integer quantity) {
}
