package br.com.balancecontrol.dataload;

/** Item whose legacy balance differs from {@code initial - confirmed orders}. */
public record DivergenceRow(long itemId, String itemCode, int initialBalance, int legacyBalance,
                            long confirmedQuantity, long computedBalance) {
}
