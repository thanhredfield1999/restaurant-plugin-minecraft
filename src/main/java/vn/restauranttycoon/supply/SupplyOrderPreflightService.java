package vn.restauranttycoon.supply;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import vn.restauranttycoon.supplysetup.SupplyRoute;
import vn.restauranttycoon.supplysetup.SupplyRouteRepository;
import vn.restauranttycoon.supplysetup.SupplySetupOwner;
import vn.restauranttycoon.supplysetup.SupplySetupPointRepository;
import vn.restauranttycoon.supplysetup.SupplySetupReadiness;
import vn.restauranttycoon.supplysetup.SupplySetupReadinessEvaluator;
import vn.restauranttycoon.supplysetup.SupplySetupReadinessIssue;

public final class SupplyOrderPreflightService {
    private final DataSource dataSource;

    public SupplyOrderPreflightService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public SupplyOrderPreflight check(String plotId, UUID playerId) throws SQLException {
        Objects.requireNonNull(plotId, "plotId");
        Objects.requireNonNull(playerId, "playerId");
        if (!ownsPlot(plotId, playerId)) {
            return new SupplyOrderPreflight(SupplyOrderPreflight.Status.NOT_OWNER,
                    List.of("Plot is not assigned to this player"));
        }
        SupplySetupPointRepository points = new SupplySetupPointRepository(dataSource);
        SupplyRouteRepository routes = new SupplyRouteRepository(dataSource);
        SupplySetupReadinessEvaluator evaluator = new SupplySetupReadinessEvaluator();
        SupplySetupOwner central = SupplySetupOwner.centralSupplier();
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant(plotId);
        SupplySetupReadiness centralReadiness = evaluator.assess(central, points.findAll(central), null);
        SupplyRoute route = routes.findAll(restaurant);
        SupplySetupReadiness restaurantReadiness = evaluator.assess(
                restaurant, points.findAll(restaurant), route);
        List<String> details = new ArrayList<>();
        addIssues("CENTRAL", centralReadiness, details);
        addIssues("RESTAURANT", restaurantReadiness, details);
        return details.isEmpty()
                ? new SupplyOrderPreflight(SupplyOrderPreflight.Status.READY, List.of())
                : new SupplyOrderPreflight(SupplyOrderPreflight.Status.SETUP_INCOMPLETE, details);
    }

    private boolean ownsPlot(String plotId, UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT account_id FROM plot_assignments WHERE plot_id = ?")) {
            statement.setString(1, plotId);
            try (var result = statement.executeQuery()) {
                return result.next() && playerId.equals(result.getObject(1, UUID.class));
            }
        }
    }

    private static void addIssues(String scope, SupplySetupReadiness readiness, List<String> details) {
        for (SupplySetupReadinessIssue issue : readiness.issues()) {
            details.add(scope + ":" + issue.type()
                    + issue.pointType().map(type -> ":" + type.name()).orElse(""));
        }
    }
}
