package vn.restauranttycoon.supplysetup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SupplySetupPointPresenter {
    private final SupplySetupMessages messages;

    public SupplySetupPointPresenter(SupplySetupMessages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public SupplySetupPointView present(
            SupplySetupPointType type,
            Optional<SupplySetupPoint> configuredPoint
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(configuredPoint, "configuredPoint");
        SupplySetupPointStatus status = configuredPoint.isPresent()
                ? SupplySetupPointStatus.CONFIGURED
                : SupplySetupPointStatus.UNSET;
        List<String> lore = new ArrayList<>();
        lore.add(messages.text("point." + type.name() + ".description"));
        lore.add(messages.text("lore.status", Map.of(
                "status", messages.text("status." + status.name()))));
        configuredPoint.ifPresent(point -> lore.add(location(point.position())));
        lore.add("");
        lore.add(messages.text("lore.set"));
        lore.add(messages.text("lore.teleport"));
        lore.add(messages.text("lore.delete"));
        if (configuredPoint.isPresent()) {
            lore.add(messages.text("lore.overwrite-warning"));
        }
        return new SupplySetupPointView(
                messages.text("point." + type.name() + ".name"),
                lore,
                status);
    }

    private String location(SupplySetupPosition position) {
        return messages.text("lore.location", Map.of(
                "world", position.worldName(),
                "x", format(position.x()),
                "y", format(position.y()),
                "z", format(position.z())));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
