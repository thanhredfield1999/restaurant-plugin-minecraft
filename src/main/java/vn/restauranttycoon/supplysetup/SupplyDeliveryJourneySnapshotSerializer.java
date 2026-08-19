package vn.restauranttycoon.supplysetup;

import java.util.Locale;
import java.util.Objects;

/** Canonical, bounded payload for durable route pinning. */
public final class SupplyDeliveryJourneySnapshotSerializer {
    public static final int VERSION = 1;
    public static final int MAX_PAYLOAD_LENGTH = 16_384;

    private SupplyDeliveryJourneySnapshotSerializer() {
    }

    public static String serialize(SupplyDeliveryJourneyPlan plan) {
        Objects.requireNonNull(plan, "plan");
        StringBuilder json = new StringBuilder("{\"version\":1,\"owner\":\"")
                .append(escape(plan.owner().ownerId()))
                .append("\",\"steps\":[");
        for (int i = 0; i < plan.steps().size(); i++) {
            if (i > 0) json.append(',');
            SupplyDeliveryJourneyStep step = plan.steps().get(i);
            SupplySetupPosition p = step.position();
            json.append("{\"stage\":\"")
                    .append(step.stage().name())
                    .append("\",\"world\":\"")
                    .append(escape(p.worldName()))
                    .append("\",\"x\":")
                    .append(number(p.x()))
                    .append(",\"y\":")
                    .append(number(p.y()))
                    .append(",\"z\":")
                    .append(number(p.z()))
                    .append(",\"yaw\":")
                    .append(number(p.yaw()))
                    .append(",\"pitch\":")
                    .append(number(p.pitch()))
                    .append('}');
        }
        String payload = json.append("]}").toString();
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("journey snapshot exceeds durable payload limit");
        }
        return payload;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) throw new IllegalArgumentException("control character in snapshot");
                    escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }
}
