package br.com.balancecontrol.order.dto;

import javax.validation.constraints.Size;

public record CancelOrderRequest(@Size(max = 200) String reason) {
}
