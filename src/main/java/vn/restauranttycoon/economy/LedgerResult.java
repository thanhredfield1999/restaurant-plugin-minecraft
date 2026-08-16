package vn.restauranttycoon.economy;

public record LedgerResult(CurrencyAmount balance, long revision, boolean duplicate) {
}
