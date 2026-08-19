package vn.restauranttycoon.supply;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyPlan;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyPlanner;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshotCodec;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshotSerializer;
import vn.restauranttycoon.supplysetup.SupplyRoute;
import vn.restauranttycoon.supplysetup.SupplyRouteWaypoint;
import vn.restauranttycoon.supplysetup.SupplySetupOwner;
import vn.restauranttycoon.supplysetup.SupplySetupPoint;
import vn.restauranttycoon.supplysetup.SupplySetupPointType;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;
import vn.restauranttycoon.supplysetup.SupplySetupScope;

public final class SupplyFulfillmentRepository {
    private final DataSource dataSource;

    public SupplyFulfillmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SupplyFulfillmentRecord createForPaidOrder(UUID orderId, UUID restaurantId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockPaidOrder(connection, orderId, restaurantId);
                SupplyFulfillmentRecord existing = find(connection, orderId);
                if (existing != null) {
                    connection.commit();
                    return existing;
                }
                UUID shipmentId = UUID.randomUUID();
                UUID packageId = UUID.randomUUID();
                insertShipment(connection, shipmentId, orderId, restaurantId);
                insertPackage(connection, packageId, shipmentId);
                copyOrderLines(connection, orderId, packageId);
                insertPendingRuntimeSnapshot(connection, shipmentId);
                connection.commit();
                return new SupplyFulfillmentRecord(
                        shipmentId, packageId, SupplyShipmentState.CREATED, SupplyPackageState.IN_TRANSIT);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void dispatch(UUID shipmentId) throws SQLException {
        updateShipmentState(shipmentId, "CREATED", "IN_TRANSIT");
    }

    public Optional<SupplyRuntimeClaim> claimNext(String instanceId, Duration lease) throws SQLException {
        validateInstanceId(instanceId);
        long leaseSeconds = validateLease(lease);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<UUID> candidate = findClaimable(connection);
                if (candidate.isEmpty()) {
                    connection.rollback();
                    return Optional.empty();
                }
                UUID claimToken = UUID.randomUUID();
                OffsetDateTime claimExpiresAt = databaseNow(connection).plusSeconds(leaseSeconds);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_shipments
                        SET claimed_by_instance = ?, claim_token = ?, claim_expires_at = ?,
                            attempt_count = attempt_count + 1, last_error = NULL
                        WHERE shipment_id = ?
                          AND state IN ('CREATED', 'IN_TRANSIT', 'ARRIVED')
                          AND (claim_expires_at IS NULL OR claim_expires_at <= CURRENT_TIMESTAMP)
                        """)) {
                    statement.setString(1, instanceId);
                    statement.setObject(2, claimToken);
                    statement.setObject(3, claimExpiresAt);
                    statement.setObject(4, candidate.get());
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                SupplyRuntimeClaim claim = loadClaim(connection, candidate.get(), claimToken);
                connection.commit();
                return Optional.of(claim);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public SupplyRuntimeClaim renew(SupplyRuntimeClaim claim, Duration lease) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        long leaseSeconds = validateLease(lease);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                OffsetDateTime claimExpiresAt = databaseNow(connection).plusSeconds(leaseSeconds);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_shipments SET claim_expires_at = ?
                        WHERE shipment_id = ? AND state IN ('CREATED', 'IN_TRANSIT', 'ARRIVED')
                          AND claimed_by_instance = ? AND claim_token = ?
                          AND claim_expires_at > CURRENT_TIMESTAMP
                        """)) {
                    statement.setObject(1, claimExpiresAt);
                    bindClaim(statement, claim, 2);
                    if (statement.executeUpdate() != 1) {
                        throw new StaleSupplyRuntimeClaimException(claim);
                    }
                }
                SupplyRuntimeClaim renewed = loadClaim(connection, claim.shipmentId(), claim.claimToken());
                connection.commit();
                return renewed;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void dispatch(SupplyRuntimeClaim claim) throws SQLException {
        transitionClaimedShipment(claim, "CREATED", "IN_TRANSIT");
    }

    public void pinRuntimeSnapshot(
            UUID shipmentId, UUID restaurantId, int snapshotVersion, String payload) throws SQLException {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(payload, "payload");
        if (snapshotVersion < 1 || payload.isBlank() || payload.length() > 16_384) {
            throw new IllegalArgumentException("Invalid runtime snapshot");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE supply_shipment_runtime runtime
                    SET revision = runtime.revision + 1,
                        checkpoint_stage = 'DELIVERY_ENTRY', checkpoint_index = 0,
                        journey_snapshot_version = ?, journey_snapshot = ?,
                        unload_deadline_at = NULL, recovery_outcome = 'NONE',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE runtime.shipment_id = ?
                      AND runtime.checkpoint_stage = 'PENDING_MANUAL'
                      AND EXISTS (
                          SELECT 1 FROM supply_shipments shipment
                          WHERE shipment.shipment_id = runtime.shipment_id
                            AND shipment.restaurant_id = ?
                            AND shipment.state = 'CREATED'
                      )
                    """)) {
                statement.setInt(1, snapshotVersion);
                statement.setString(2, payload);
                statement.setObject(3, shipmentId);
                statement.setObject(4, restaurantId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Runtime snapshot cannot be pinned");
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void pinRuntimeSnapshotFromSetup(
            UUID shipmentId, UUID restaurantId, UUID playerId, String plotId) throws SQLException {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(plotId, "plotId");
        SupplySetupOwner owner = SupplySetupOwner.restaurant(plotId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<SupplySetupPoint> points = loadSetupPoints(connection, owner);
                SupplyRoute route = loadRoute(connection, owner);
                SupplyDeliveryJourneyPlan plan = new SupplyDeliveryJourneyPlanner().plan(owner, points, route);
                String payload = SupplyDeliveryJourneySnapshotSerializer.serialize(plan);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_shipment_runtime runtime
                        SET revision = revision + 1, checkpoint_stage = 'DELIVERY_ENTRY', checkpoint_index = 0,
                            journey_snapshot_version = 1, journey_snapshot = ?,
                            unload_deadline_at = NULL, recovery_outcome = 'NONE',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE runtime.shipment_id = ? AND runtime.checkpoint_stage = 'PENDING_MANUAL'
                          AND EXISTS (SELECT 1 FROM supply_shipments shipment
                                      WHERE shipment.shipment_id = runtime.shipment_id
                                        AND shipment.restaurant_id = ? AND shipment.state = 'CREATED')
                      AND EXISTS (SELECT 1 FROM plot_assignments assignment
                                  WHERE assignment.plot_id = ? AND assignment.account_id = ?)
                        """)) {
                    statement.setString(1, payload);
                    statement.setObject(2, shipmentId);
                    statement.setObject(3, restaurantId);
                    statement.setString(4, plotId);
                    statement.setObject(5, playerId);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("Runtime snapshot cannot be pinned");
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public SupplyRuntimeTransitionResult transitionCheckpoint(
            SupplyRuntimeClaim claim, long expectedRevision, String expectedStage, int expectedIndex,
            String nextStage, int nextIndex, UUID operationId) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(expectedStage, "expectedStage");
        Objects.requireNonNull(nextStage, "nextStage");
        Objects.requireNonNull(operationId, "operationId");
        if (expectedRevision < 1 || expectedIndex < 0 || nextIndex < 0) {
            throw new IllegalArgumentException("invalid checkpoint transition payload");
        }
        SupplyRuntimeCheckpointTransition.requireNext(expectedStage, nextStage);
        if (!"ROUTE_WAYPOINT".equals(nextStage) && nextIndex != 0) {
            throw new IllegalArgumentException("non-waypoint next index must be zero");
        }
        if ("ROUTE_WAYPOINT".equals(expectedStage) && "ROUTE_WAYPOINT".equals(nextStage)
                && nextIndex != expectedIndex + 1) {
            throw new IllegalArgumentException("route waypoint index must advance by one");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String currentOperation = null;
                long revision;
                String currentStage;
                int currentIndex;
                int snapshotVersion;
                String snapshotPayload;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT revision, checkpoint_stage, checkpoint_index, last_transition_operation_id,
                               journey_snapshot_version, journey_snapshot
                        FROM supply_shipment_runtime runtime
                        JOIN supply_shipments shipment ON shipment.shipment_id = runtime.shipment_id
                        WHERE runtime.shipment_id = ?
                          AND shipment.claimed_by_instance = ? AND shipment.claim_token = ?
                          AND shipment.claim_expires_at > CURRENT_TIMESTAMP
                        FOR UPDATE OF runtime, shipment
                        """)) {
                    statement.setObject(1, claim.shipmentId());
                    statement.setString(2, claim.instanceId());
                    statement.setObject(3, claim.claimToken());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("Runtime row is missing");
                        revision = result.getLong(1); currentStage = result.getString(2);
                        currentIndex = result.getInt(3);
                        UUID op = result.getObject(4, UUID.class);
                        currentOperation = op == null ? null : op.toString();
                        snapshotVersion = result.getInt(5);
                        snapshotPayload = result.getString(6);
                    }
                }
                if (snapshotVersion != 1) throw new IllegalStateException("unsupported journey snapshot version");
                SupplyRuntimeCheckpointTransition.requireNext(
                        SupplyDeliveryJourneySnapshotCodec.decode(snapshotPayload),
                        expectedStage, expectedIndex, nextStage, nextIndex);
                if (operationId.toString().equals(currentOperation)) {
                    if (revision != expectedRevision + 1 || !nextStage.equals(currentStage) || currentIndex != nextIndex) {
                        throw new IllegalStateException("checkpoint operation payload conflict");
                    }
                    connection.commit();
                    return SupplyRuntimeTransitionResult.IDEMPOTENT_REPLAY;
                }
                if (revision != expectedRevision || !expectedStage.equals(currentStage) || currentIndex != expectedIndex) {
                    throw new StaleSupplyRuntimeClaimException(claim);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_shipment_runtime
                        SET revision = revision + 1, checkpoint_stage = ?, checkpoint_index = ?,
                            last_transition_operation_id = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE shipment_id = ? AND revision = ? AND checkpoint_stage = ? AND checkpoint_index = ?
                        """)) {
                    statement.setString(1, nextStage); statement.setInt(2, nextIndex);
                    statement.setObject(3, operationId); statement.setObject(4, claim.shipmentId());
                    statement.setLong(5, expectedRevision); statement.setString(6, expectedStage); statement.setInt(7, expectedIndex);
                    if (statement.executeUpdate() != 1) throw new StaleSupplyRuntimeClaimException(claim);
                }
                connection.commit();
                return SupplyRuntimeTransitionResult.APPLIED;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            }
        }
    }

    public void markPendingManual(SupplyRuntimeClaim claim) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE supply_shipment_runtime runtime
                    SET revision = revision + 1, checkpoint_stage = 'PENDING_MANUAL', checkpoint_index = 0,
                        unload_deadline_at = NULL, recovery_outcome = 'PENDING_MANUAL',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE runtime.shipment_id = ? AND runtime.checkpoint_stage <> 'PENDING_MANUAL'
                      AND EXISTS (SELECT 1 FROM supply_shipments shipment
                                  WHERE shipment.shipment_id = runtime.shipment_id
                                    AND shipment.claimed_by_instance = ?
                                    AND shipment.claim_token = ?
                                    AND shipment.claim_expires_at > CURRENT_TIMESTAMP)
                    """)) {
                statement.setObject(1, claim.shipmentId());
                statement.setString(2, claim.instanceId());
                statement.setObject(3, claim.claimToken());
                if (statement.executeUpdate() != 1) {
                    try (PreparedStatement check = connection.prepareStatement("""
                            SELECT 1 FROM supply_shipment_runtime runtime
                            WHERE runtime.shipment_id = ? AND runtime.checkpoint_stage = 'PENDING_MANUAL'
                              AND EXISTS (SELECT 1 FROM supply_shipments shipment
                                          WHERE shipment.shipment_id = runtime.shipment_id
                                            AND shipment.claimed_by_instance = ?
                                            AND shipment.claim_token = ?
                                            AND shipment.claim_expires_at > CURRENT_TIMESTAMP)
                            """)) {
                        check.setObject(1, claim.shipmentId());
                        check.setString(2, claim.instanceId());
                        check.setObject(3, claim.claimToken());
                        try (ResultSet result = check.executeQuery()) {
                            if (!result.next()) throw new StaleSupplyRuntimeClaimException(claim);
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<SupplyRuntimeProjection> findRuntimeProjection(UUID shipmentId, UUID restaurantId)
            throws SQLException {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.shipment_id, p.package_id, s.restaurant_id, r.revision,
                            r.checkpoint_stage, r.checkpoint_index, r.journey_snapshot_version, r.journey_snapshot,
                            r.recovery_outcome
                     FROM supply_shipments s
                     JOIN supply_packages p ON p.shipment_id = s.shipment_id
                     JOIN supply_shipment_runtime r ON r.shipment_id = s.shipment_id
                     WHERE s.shipment_id = ? AND s.restaurant_id = ?
                     """)) {
            statement.setObject(1, shipmentId);
            statement.setObject(2, restaurantId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                if (!"NONE".equals(result.getString(9))) return Optional.empty();
                int checkpointIndex = result.getInt(6);
                int version = result.getInt(7);
                if (version != 1) return Optional.empty();
                SupplyDeliveryJourneySnapshot journey = SupplyDeliveryJourneySnapshotCodec.decode(result.getString(8));
                return Optional.of(new SupplyRuntimeProjection(
                        result.getObject(1, UUID.class), result.getObject(2, UUID.class),
                        result.getObject(3, UUID.class), result.getLong(4), result.getString(5), checkpointIndex, journey));
            } catch (IllegalArgumentException invalidSnapshot) {
                return Optional.empty();
            }
        }
    }

    public boolean hasRecoverableRuntimeSnapshot(UUID shipmentId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT checkpoint_stage, journey_snapshot_version, journey_snapshot,
                            recovery_outcome
                     FROM supply_shipment_runtime
                     WHERE shipment_id = ?
                     """)) {
            statement.setObject(1, shipmentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return false;
                String checkpoint = result.getString(1);
                int version = result.getInt(2);
                String snapshot = result.getString(3);
                String recovery = result.getString(4);
                return !"PENDING_MANUAL".equals(checkpoint)
                        && "NONE".equals(recovery)
                        && version >= 1
                        && snapshot != null
                        && !snapshot.isBlank();
            }
        }
    }

    public void releaseClaim(SupplyRuntimeClaim claim) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE supply_shipments
                     SET claimed_by_instance = NULL, claim_token = NULL, claim_expires_at = NULL
                     WHERE shipment_id = ? AND claimed_by_instance = ? AND claim_token = ?
                     """)) {
            statement.setObject(1, claim.shipmentId());
            statement.setString(2, claim.instanceId());
            statement.setObject(3, claim.claimToken());
            statement.executeUpdate();
        }
    }

    public void arrive(UUID shipmentId) throws SQLException {
        updateShipmentState(shipmentId, "IN_TRANSIT", "ARRIVED");
    }

    private void updateShipmentState(UUID shipmentId, String expected, String next) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE supply_shipments SET state = ? WHERE shipment_id = ? AND state = ?")) {
            statement.setString(1, next);
            statement.setObject(2, shipmentId);
            statement.setString(3, expected);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Invalid shipment transition");
            }
        }
    }

    private void transitionClaimedShipment(SupplyRuntimeClaim claim, String expected, String next)
            throws SQLException {
        Objects.requireNonNull(claim, "claim");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE supply_shipments SET state = ?
                     WHERE shipment_id = ? AND state = ?
                       AND claimed_by_instance = ? AND claim_token = ?
                       AND claim_expires_at > CURRENT_TIMESTAMP
                     """)) {
            statement.setString(1, next);
            statement.setObject(2, claim.shipmentId());
            statement.setString(3, expected);
            statement.setString(4, claim.instanceId());
            statement.setObject(5, claim.claimToken());
            if (statement.executeUpdate() != 1) {
                throw new StaleSupplyRuntimeClaimException(claim);
            }
        }
    }

    public void handoffPackageAuthorized(
            UUID packageId, UUID restaurantId, UUID receivingActorId, UUID operationId) throws SQLException {
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(receivingActorId, "receivingActorId");
        Objects.requireNonNull(operationId, "operationId");
        UUID shipmentId;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT shipment_id FROM supply_packages WHERE package_id = ?")) {
            statement.setObject(1, packageId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Package does not exist");
                shipmentId = result.getObject(1, UUID.class);
            }
        }
        handoffAuthorized(shipmentId, restaurantId, receivingActorId, operationId);
    }

    public void handoffPackageAuthorizedForOrderPlayer(
            UUID packageId, UUID playerId, UUID operationId) throws SQLException {
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID shipmentId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT s.shipment_id
                        FROM supply_packages p
                        JOIN supply_shipments s ON s.shipment_id = p.shipment_id
                        JOIN supply_orders o ON o.order_id = s.order_id
                        WHERE p.package_id = ? AND o.player_id = ?
                        FOR UPDATE OF p, s
                        """)) {
                    statement.setObject(1, packageId);
                    statement.setObject(2, playerId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Player is not authorized for package restaurant");
                        }
                        shipmentId = result.getObject(1, UUID.class);
                    }
                }
                applyHandoff(connection, shipmentId, playerId, operationId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void handoffAuthorized(
            UUID shipmentId, UUID restaurantId, UUID receivingActorId, UUID operationId) throws SQLException {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(receivingActorId, "receivingActorId");
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireShipmentRestaurant(connection, shipmentId, restaurantId);
                applyHandoff(connection, shipmentId, receivingActorId, operationId);
                connection.commit();

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void handoff(UUID shipmentId, UUID playerId, UUID operationId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_shipments SET state = 'HANDED_OFF', handoff_operation_id = ?
                        WHERE shipment_id = ? AND state = 'ARRIVED'
                        """)) {
                    statement.setObject(1, operationId);
                    statement.setObject(2, shipmentId);
                    if (statement.executeUpdate() == 0 && !handoffAlreadyApplied(connection, shipmentId, operationId)) {
                        throw new IllegalStateException("Invalid handoff");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_packages SET state = 'HANDED_OFF', received_by = ?, received_at = CURRENT_TIMESTAMP
                        WHERE shipment_id = ? AND state = 'IN_TRANSIT'
                        """)) {
                    statement.setObject(1, playerId);
                    statement.setObject(2, shipmentId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void requireShipmentRestaurant(Connection connection, UUID shipmentId, UUID restaurantId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT restaurant_id FROM supply_shipments WHERE shipment_id = ? FOR UPDATE")) {
            statement.setObject(1, shipmentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !restaurantId.equals(result.getObject(1, UUID.class))) {
                    throw new IllegalStateException("Shipment does not belong to restaurant");
                }
            }
        }
    }

    private void applyHandoff(Connection connection, UUID shipmentId, UUID receivingActorId, UUID operationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE supply_shipments SET state = 'HANDED_OFF', handoff_operation_id = ?
                WHERE shipment_id = ? AND state = 'ARRIVED'
                """)) {
            statement.setObject(1, operationId);
            statement.setObject(2, shipmentId);
            if (statement.executeUpdate() == 0 && !handoffAlreadyApplied(connection, shipmentId, operationId)) {
                throw new IllegalStateException("Invalid handoff");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE supply_packages SET state = 'HANDED_OFF', received_by = ?, received_at = CURRENT_TIMESTAMP
                WHERE shipment_id = ? AND state = 'IN_TRANSIT'
                """)) {
            statement.setObject(1, receivingActorId);
            statement.setObject(2, shipmentId);
            if (statement.executeUpdate() != 1
                    && !handoffAlreadyApplied(connection, shipmentId, operationId, receivingActorId)) {
                throw new IllegalStateException("Package is not ready for handoff");
            }
        }
    }

    public void handoff(UUID shipmentId, UUID operationId) throws SQLException {
        handoff(shipmentId, operationId, operationId);
    }

    public void stock(UUID packageId, UUID restaurantId, UUID operationId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String packageState;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT p.state FROM supply_packages p
                        JOIN supply_shipments shipment ON shipment.shipment_id = p.shipment_id
                        WHERE p.package_id = ? AND shipment.restaurant_id = ?
                        FOR UPDATE OF p
                        """)) {
                    statement.setObject(1, packageId);
                    statement.setObject(2, restaurantId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Package is not ready for stock at this restaurant");
                        }
                        packageState = result.getString(1);
                    }
                }
                if (stockOperationExists(connection, packageId)) {
                    if (!stockOperationMatches(connection, packageId, restaurantId, operationId)) {
                        throw new IllegalStateException("Package stock operation payload mismatch");
                    }
                    connection.commit();
                    return;
                }
                if (!"HANDED_OFF".equals(packageState)) {
                    throw new IllegalStateException("Package is not ready for stock at this restaurant");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO warehouse_stock_operations (operation_id, package_id, restaurant_id) VALUES (?, ?, ?)")) {
                    statement.setObject(1, operationId);
                    statement.setObject(2, packageId);
                    statement.setObject(3, restaurantId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT sku, quantity FROM supply_package_lines WHERE package_id = ?")) {
                    statement.setObject(1, packageId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            addStock(connection, restaurantId, result.getString(1), result.getInt(2));
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE supply_packages SET state = 'STOCKED', stocked_operation_id = ? WHERE package_id = ?")) {
                    statement.setObject(1, operationId);
                    statement.setObject(2, packageId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void stockForOrderPlayer(UUID packageId, UUID playerId, UUID operationId) throws SQLException {
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID restaurantId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT s.restaurant_id
                        FROM supply_packages p
                        JOIN supply_shipments s ON s.shipment_id = p.shipment_id
                        JOIN supply_orders o ON o.order_id = s.order_id
                        WHERE p.package_id = ? AND o.player_id = ?
                        FOR UPDATE OF p, s
                        """)) {
                    statement.setObject(1, packageId);
                    statement.setObject(2, playerId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Player is not authorized for package restaurant");
                        }
                        restaurantId = result.getObject(1, UUID.class);
                    }
                }
                stockInLockedTransaction(connection, packageId, restaurantId, operationId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void stockInLockedTransaction(
            Connection connection, UUID packageId, UUID restaurantId, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.state FROM supply_packages p
                JOIN supply_shipments shipment ON shipment.shipment_id = p.shipment_id
                WHERE p.package_id = ? AND shipment.restaurant_id = ?
                FOR UPDATE OF p
                """)) {
            statement.setObject(1, packageId);
            statement.setObject(2, restaurantId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"HANDED_OFF".equals(result.getString(1))) {
                    if (!stockOperationMatches(connection, packageId, restaurantId, operationId)) {
                        throw new IllegalStateException("Package is not ready for stock at this restaurant");
                    }
                    return;
                }
            }
        }
        if (stockOperationExists(connection, packageId)) {
            if (!stockOperationMatches(connection, packageId, restaurantId, operationId)) {
                throw new IllegalStateException("Package stock operation payload mismatch");
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO warehouse_stock_operations (operation_id, package_id, restaurant_id) VALUES (?, ?, ?)")) {
            statement.setObject(1, operationId);
            statement.setObject(2, packageId);
            statement.setObject(3, restaurantId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sku, quantity FROM supply_package_lines WHERE package_id = ?")) {
            statement.setObject(1, packageId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) addStock(connection, restaurantId, result.getString(1), result.getInt(2));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE supply_packages SET state = 'STOCKED', stocked_operation_id = ? WHERE package_id = ?")) {
            statement.setObject(1, operationId);
            statement.setObject(2, packageId);
            statement.executeUpdate();
        }
    }

    public SupplyDeliveryFinalizeResult finalizeDelivery(
            UUID shipmentId, long expectedRevision, UUID operationId) throws SQLException {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(operationId, "operationId");
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be positive");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long revision;
                String stage;
                String cleanupState;
                UUID cleanupOperation;
                UUID previousOperation;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT runtime.revision, runtime.checkpoint_stage, runtime.last_transition_operation_id,
                               runtime.entity_cleanup_state, runtime.entity_cleanup_operation_id
                        FROM supply_shipment_runtime runtime
                        JOIN supply_shipments shipment ON shipment.shipment_id = runtime.shipment_id
                        JOIN supply_packages package_row ON package_row.shipment_id = shipment.shipment_id
                        WHERE runtime.shipment_id = ?
                          AND shipment.state = 'HANDED_OFF' AND package_row.state = 'STOCKED'
                        FOR UPDATE OF runtime, shipment, package_row
                        """)) {
                    statement.setObject(1, shipmentId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("Delivery is not finalized-ready");
                        revision = result.getLong(1); stage = result.getString(2);
                        previousOperation = result.getObject(3, UUID.class);
                        cleanupState = result.getString(4);
                        cleanupOperation = result.getObject(5, UUID.class);
                    }
                }
                if (operationId.equals(previousOperation)) {
                    if (revision != expectedRevision + 1 || !"DELIVERY_DESPAWN".equals(stage)
                            || !operationId.equals(cleanupOperation)) {
                        throw new IllegalStateException("Finalize operation payload conflict");
                    }
                    connection.commit();
                    return SupplyDeliveryFinalizeResult.IDEMPOTENT_REPLAY;
                }
                if (revision != expectedRevision || !"DELIVERY_DESPAWN".equals(stage)
                        || !"NONE".equals(cleanupState)) {
                    throw new IllegalStateException("Delivery is not ready for finalization");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_shipment_runtime
                        SET revision = revision + 1, last_transition_operation_id = ?,
                            entity_cleanup_state = 'REQUIRED', entity_cleanup_operation_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE shipment_id = ? AND revision = ? AND checkpoint_stage = 'DELIVERY_DESPAWN'
                        """)) {
                    statement.setObject(1, operationId); statement.setObject(2, operationId);
                    statement.setObject(3, shipmentId); statement.setLong(4, expectedRevision);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("Delivery finalization lost compare-and-set");
                }
                connection.commit();
                return SupplyDeliveryFinalizeResult.APPLIED;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean confirmEntityCleanup(UUID shipmentId, UUID operationId) throws SQLException {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE supply_shipment_runtime
                        SET entity_cleanup_state = 'CONFIRMED', revision = revision + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE shipment_id = ? AND entity_cleanup_state = 'REQUIRED'
                          AND entity_cleanup_operation_id = ?
                        """)) {
                    statement.setObject(1, shipmentId); statement.setObject(2, operationId);
                    int changed = statement.executeUpdate();
                    if (changed == 1) {
                        connection.commit();
                        return true;
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT 1 FROM supply_shipment_runtime
                        WHERE shipment_id = ? AND entity_cleanup_state = 'CONFIRMED'
                          AND entity_cleanup_operation_id = ?
                        """)) {
                    statement.setObject(1, shipmentId); statement.setObject(2, operationId);
                    try (ResultSet result = statement.executeQuery()) {
                        boolean confirmed = result.next();
                        connection.commit();
                        return confirmed;
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<SupplyRuntimeWork> findRuntimeWork(int limit) throws SQLException {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        List<SupplyRuntimeWork> work = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.shipment_id, p.package_id, s.restaurant_id, s.state, p.state
                     FROM supply_shipments s JOIN supply_packages p ON p.shipment_id = s.shipment_id
                     WHERE s.state NOT IN ('HANDED_OFF', 'CANCELLED') AND p.state <> 'STOCKED'
                     ORDER BY s.shipment_id LIMIT ?
                     """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    work.add(new SupplyRuntimeWork(
                            result.getObject(1, UUID.class), result.getObject(2, UUID.class),
                            result.getObject(3, UUID.class), SupplyShipmentState.valueOf(result.getString(4)),
                            SupplyPackageState.valueOf(result.getString(5))));
                }
            }
        }
        return List.copyOf(work);
    }

    public int quantity(UUID restaurantId, String sku) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT quantity FROM warehouse_stock WHERE restaurant_id = ? AND sku = ?")) {
            statement.setObject(1, restaurantId);
            statement.setString(2, sku);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private Optional<UUID> findClaimable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT shipment_id FROM supply_shipments
                WHERE state IN ('CREATED', 'IN_TRANSIT', 'ARRIVED')
                  AND (claim_expires_at IS NULL OR claim_expires_at <= CURRENT_TIMESTAMP)
                ORDER BY shipment_id FETCH FIRST 1 ROW ONLY FOR UPDATE SKIP LOCKED
                """);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? Optional.of(result.getObject(1, UUID.class)) : Optional.empty();
        }
    }

    private SupplyRuntimeClaim loadClaim(Connection connection, UUID shipmentId, UUID claimToken)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.shipment_id, p.package_id, s.restaurant_id, s.state, p.state,
                       s.attempt_count, s.claimed_by_instance, s.claim_token, s.claim_expires_at
                FROM supply_shipments s JOIN supply_packages p ON p.shipment_id = s.shipment_id
                WHERE s.shipment_id = ? AND s.claim_token = ?
                """)) {
            statement.setObject(1, shipmentId);
            statement.setObject(2, claimToken);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Claim disappeared while loading supply shipment");
                }
                return new SupplyRuntimeClaim(
                        result.getObject(1, UUID.class), result.getObject(2, UUID.class),
                        result.getObject(3, UUID.class), SupplyShipmentState.valueOf(result.getString(4)),
                        SupplyPackageState.valueOf(result.getString(5)), result.getInt(6), result.getString(7),
                        result.getObject(8, UUID.class), result.getObject(9, OffsetDateTime.class).toInstant());
            }
        }
    }

    private boolean handoffAlreadyApplied(Connection connection, UUID shipmentId, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM supply_shipments WHERE shipment_id = ? AND handoff_operation_id = ?")) {
            statement.setObject(1, shipmentId);
            statement.setObject(2, operationId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean handoffAlreadyApplied(
            Connection connection, UUID shipmentId, UUID operationId, UUID receivingActorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM supply_shipments s
                JOIN supply_packages p ON p.shipment_id = s.shipment_id
                WHERE s.shipment_id = ? AND s.handoff_operation_id = ? AND p.received_by = ?
                """)) {
            statement.setObject(1, shipmentId);
            statement.setObject(2, operationId);
            statement.setObject(3, receivingActorId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean stockOperationExists(Connection connection, UUID packageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM warehouse_stock_operations WHERE package_id = ?")) {
            statement.setObject(1, packageId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean stockOperationMatches(
            Connection connection, UUID packageId, UUID restaurantId, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM warehouse_stock_operations
                WHERE package_id = ? AND restaurant_id = ? AND operation_id = ?
                """)) {
            statement.setObject(1, packageId);
            statement.setObject(2, restaurantId);
            statement.setObject(3, operationId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void addStock(Connection connection, UUID restaurantId, String sku, int quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO warehouse_stock AS target
                USING (VALUES (?, ?, ?)) AS source (restaurant_id, sku, quantity)
                ON target.restaurant_id = source.restaurant_id AND target.sku = source.sku
                WHEN MATCHED THEN UPDATE SET quantity = target.quantity + source.quantity
                WHEN NOT MATCHED THEN INSERT (restaurant_id, sku, quantity)
                VALUES (source.restaurant_id, source.sku, source.quantity)
                """)) {
            statement.setObject(1, restaurantId);
            statement.setString(2, sku);
            statement.setInt(3, quantity);
            statement.executeUpdate();
        }
    }

    private void lockPaidOrder(Connection connection, UUID orderId, UUID restaurantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT o.order_id FROM supply_orders o JOIN supply_payments p ON p.order_id = o.order_id
                WHERE o.order_id = ? AND o.restaurant_id = ?
                  AND o.state = 'SUBMITTED' AND p.state = 'CAPTURED' FOR UPDATE
                """)) {
            statement.setObject(1, orderId);
            statement.setObject(2, restaurantId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Order is not paid and submit-ready");
                }
            }
        }
    }

    private SupplyFulfillmentRecord find(Connection connection, UUID orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.shipment_id, s.state, p.package_id, p.state
                FROM supply_shipments s JOIN supply_packages p ON p.shipment_id = s.shipment_id
                WHERE s.order_id = ?
                """)) {
            statement.setObject(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new SupplyFulfillmentRecord(
                        result.getObject(1, UUID.class), result.getObject(3, UUID.class),
                        SupplyShipmentState.valueOf(result.getString(2)),
                        SupplyPackageState.valueOf(result.getString(4)));
            }
        }
    }

    private void insertShipment(Connection connection, UUID shipmentId, UUID orderId, UUID restaurantId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_shipments (shipment_id, order_id, restaurant_id, state) VALUES (?, ?, ?, 'CREATED')")) {
            statement.setObject(1, shipmentId);
            statement.setObject(2, orderId);
            statement.setObject(3, restaurantId);
            statement.executeUpdate();
        }
    }

    private List<SupplySetupPoint> loadSetupPoints(Connection connection, SupplySetupOwner owner)
            throws SQLException {
        List<SupplySetupPoint> points = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT point_type, world_name, x, y, z, yaw, pitch
                FROM supply_setup_points WHERE setup_scope = ? AND owner_id = ?
                ORDER BY point_type
                """)) {
            statement.setString(1, owner.scope().name());
            statement.setString(2, owner.ownerId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    points.add(new SupplySetupPoint(owner,
                            SupplySetupPointType.valueOf(result.getString(1)),
                            new SupplySetupPosition(result.getString(2), result.getDouble(3),
                                    result.getDouble(4), result.getDouble(5), result.getFloat(6), result.getFloat(7))));
                }
            }
        }
        return List.copyOf(points);
    }

    private SupplyRoute loadRoute(Connection connection, SupplySetupOwner owner) throws SQLException {
        List<SupplyRouteWaypoint> waypoints = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sequence_no, waypoint_name, world_name, x, y, z, yaw, pitch
                FROM supply_route_waypoints WHERE setup_scope = ? AND owner_id = ?
                ORDER BY sequence_no
                """)) {
            statement.setString(1, owner.scope().name());
            statement.setString(2, owner.ownerId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    waypoints.add(new SupplyRouteWaypoint(owner, result.getInt(1), result.getString(2),
                            new SupplySetupPosition(result.getString(3), result.getDouble(4),
                                    result.getDouble(5), result.getDouble(6), result.getFloat(7), result.getFloat(8))));
                }
            }
        }
        return new SupplyRoute(owner, waypoints);
    }

    private void insertPendingRuntimeSnapshot(Connection connection, UUID shipmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO supply_shipment_runtime
                    (shipment_id, revision, checkpoint_stage, journey_snapshot_version,
                     journey_snapshot, recovery_outcome)
                VALUES (?, 1, 'PENDING_MANUAL', 1, ?, 'PENDING_MANUAL')
                """)) {
            statement.setObject(1, shipmentId);
            statement.setString(2, "{\"schema\":\"route-not-pinned\"}");
            statement.executeUpdate();
        }
    }

    private void insertPackage(Connection connection, UUID packageId, UUID shipmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_packages (package_id, shipment_id, state) VALUES (?, ?, 'IN_TRANSIT')")) {
            statement.setObject(1, packageId);
            statement.setObject(2, shipmentId);
            statement.executeUpdate();
        }
    }

    private void copyOrderLines(Connection connection, UUID orderId, UUID packageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_package_lines (package_id, sku, quantity) SELECT ?, sku, quantity FROM supply_order_lines WHERE order_id = ?")) {
            statement.setObject(1, packageId);
            statement.setObject(2, orderId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalStateException("Paid order has no order lines");
            }
        }
    }

    private OffsetDateTime databaseNow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Database did not return CURRENT_TIMESTAMP");
            }
            return result.getObject(1, OffsetDateTime.class);
        }
    }

    private static void bindClaim(PreparedStatement statement, SupplyRuntimeClaim claim, int start) throws SQLException {
        statement.setObject(start, claim.shipmentId());
        statement.setString(start + 1, claim.instanceId());
        statement.setObject(start + 2, claim.claimToken());
    }

    private static void validateInstanceId(String instanceId) {
        if (instanceId == null || !instanceId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("instanceId must be a safe identifier of at most 64 characters");
        }
    }

    private static long validateLease(Duration lease) {
        Objects.requireNonNull(lease, "lease");
        long seconds = lease.getSeconds();
        if (seconds < 1 || seconds > 300 || lease.getNano() != 0) {
            throw new IllegalArgumentException("lease must be a whole number of seconds from 1 to 300");
        }
        return seconds;
    }
}
