package vn.restauranttycoon.supplysetup;

import java.util.List;
import java.util.Objects;

public record SupplyDeliveryJourneySnapshot(int version, String ownerId, List<SupplyDeliveryJourneyStep> steps) {
    public SupplyDeliveryJourneySnapshot {
        if (version < 1) throw new IllegalArgumentException("snapshot version must be positive");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty()) throw new IllegalArgumentException("snapshot must contain steps");
    }
}

final class SupplyDeliveryJourneySnapshotParser {
    private SupplyDeliveryJourneySnapshotParser() {}

    static SupplyDeliveryJourneySnapshot parse(String payload) {
        Objects.requireNonNull(payload, "payload");
        if (!payload.startsWith("{\"version\":1,\"owner\":\"")) throw new IllegalArgumentException("unsupported journey snapshot payload");
        int ownerEnd = payload.indexOf("\",\"steps\":[", 22);
        if (ownerEnd < 0 || !payload.endsWith("]}")) throw new IllegalArgumentException("malformed journey snapshot");
        String owner = unescape(payload.substring(22, ownerEnd));
        String body = payload.substring(ownerEnd + 11, payload.length() - 2);
        String[] entries = body.isBlank() ? new String[0] : body.split("(?<=\\}),(?=\\{)");
        java.util.ArrayList<SupplyDeliveryJourneyStep> steps = new java.util.ArrayList<>();
        for (String entry : entries) steps.add(parseStep(entry));
        return new SupplyDeliveryJourneySnapshot(1, owner, steps);
    }

    private static SupplyDeliveryJourneyStep parseStep(String entry) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\\\"stage\\\":\\\"([^\\\"]+)\\\",\\\"world\\\":\\\"((?:\\\\.|[^\\\"])*)\\\",\\\"x\\\":(-?\\d+(?:\\.\\d+)?),\\\"y\\\":(-?\\d+(?:\\.\\d+)?),\\\"z\\\":(-?\\d+(?:\\.\\d+)?),\\\"yaw\\\":(-?\\d+(?:\\.\\d+)?),\\\"pitch\\\":(-?\\d+(?:\\.\\d+)?)\\}").matcher(entry);
        if (!m.matches()) throw new IllegalArgumentException("malformed journey step");
        return new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.valueOf(m.group(1)), new SupplySetupPosition(unescape(m.group(2)), Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)), Double.parseDouble(m.group(5)), Float.parseFloat(m.group(6)), Float.parseFloat(m.group(7))));
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length()); boolean escaped = false;
        for (char c : value.toCharArray()) { if (escaped) { out.append(switch (c) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> c; }); escaped = false; } else if (c == '\\') escaped = true; else out.append(c); }
        if (escaped) throw new IllegalArgumentException("truncated escape");
        return out.toString();
    }
}
