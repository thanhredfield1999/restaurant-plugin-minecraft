package vn.restauranttycoon.dish;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class DishItemPdcCodec {
    private final NamespacedKey tokenKey;

    public DishItemPdcCodec(NamespacedKey tokenKey) {
        this.tokenKey = Objects.requireNonNull(tokenKey, "tokenKey");
    }

    public void write(ItemMeta meta, DishItemToken token) {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(token, "token");
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, token.encode());
    }

    public ReadResult read(ItemMeta meta) {
        Objects.requireNonNull(meta, "meta");
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(tokenKey)) {
            return ReadResult.absent();
        }
        if (!data.has(tokenKey, PersistentDataType.STRING)) {
            return ReadResult.invalid();
        }
        String encoded = data.get(tokenKey, PersistentDataType.STRING);
        return DishItemToken.decode(encoded)
                .map(ReadResult::valid)
                .orElseGet(ReadResult::invalid);
    }

    public enum ReadStatus {
        ABSENT,
        VALID,
        INVALID
    }

    public record ReadResult(ReadStatus status, Optional<DishItemToken> token) {
        public ReadResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(token, "token");
            if ((status == ReadStatus.VALID) != token.isPresent()) {
                throw new IllegalArgumentException("Only a valid result may contain a token");
            }
        }

        public static ReadResult absent() {
            return new ReadResult(ReadStatus.ABSENT, Optional.empty());
        }

        public static ReadResult valid(DishItemToken token) {
            return new ReadResult(ReadStatus.VALID, Optional.of(Objects.requireNonNull(token, "token")));
        }

        public static ReadResult invalid() {
            return new ReadResult(ReadStatus.INVALID, Optional.empty());
        }
    }
}
