package br.com.balancecontrol.item.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** Positive delta = stock entry; negative = write-off/correction. */
public record AdjustBalanceRequest(
        @NotNull Integer delta,
        @NotBlank @Size(max = 200) String reason) {
}
