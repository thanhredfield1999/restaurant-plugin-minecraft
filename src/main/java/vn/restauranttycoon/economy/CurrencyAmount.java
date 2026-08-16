package vn.restauranttycoon.economy;

public record CurrencyAmount(long units) {
    public CurrencyAmount {
        if (units < 0) {
            throw new IllegalArgumentException("Currency amount cannot be negative");
        }
    }

    public CurrencyAmount add(CurrencyAmount other) {
        return new CurrencyAmount(Math.addExact(units, other.units));
    }

    public CurrencyAmount subtract(CurrencyAmount other) {
        if (other.units > units) {
            throw new InsufficientFundsException(units, other.units);
        }
        return new CurrencyAmount(units - other.units);
    }
}
