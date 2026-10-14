package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyActivityType;
import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class JdbcBuggyActivityRepository implements BuggyActivityRepository {
    private final TransactionRunner transactions;

    public JdbcBuggyActivityRepository(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void append(BuggyActivityEntry entry) {
        Objects.requireNonNull(entry, "entry");
        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.buggy_activity(activity_id,assistant_id,activity_type,attention,task_id,"
                            + "automation_id,summary,details_encoded,actor_id,occurred_at) "
                            + "VALUES (?,'BUGGY',?,?,?,?,?,?,?,?)")) {
                statement.setObject(1, entry.activityId());
                statement.setString(2, entry.type().name());
                statement.setString(3, entry.attention().name());
                statement.setObject(4, entry.taskId() == null ? null : entry.taskId().value());
                statement.setString(5, entry.automationId());
                statement.setString(6, entry.summary());
                statement.setString(7, encode(entry.details()));
                statement.setString(8, entry.actorId().value());
                statement.setTimestamp(9, Timestamp.from(entry.occurredAt()));
                if (statement.executeUpdate() != 1) throw new IllegalStateException("activity insert failed");
            }
            return null;
        });
    }

    @Override
    public List<BuggyActivityEntry> recent(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit out of range");
        return transactions.readOnly(connection -> {
            ArrayList<BuggyActivityEntry> output = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT activity_id,activity_type,attention,task_id,automation_id,summary,details_encoded,actor_id,occurred_at "
                            + "FROM assistant.buggy_activity WHERE assistant_id='BUGGY' "
                            + "ORDER BY occurred_at DESC,activity_id DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID task = result.getObject("task_id", UUID.class);
                        output.add(new BuggyActivityEntry(
                                result.getObject("activity_id", UUID.class),
                                BuggyActivityType.valueOf(result.getString("activity_type")),
                                BuggyAttentionLevel.valueOf(result.getString("attention")),
                                task == null ? null : new BuggyTaskId(task),
                                result.getString("automation_id"),
                                result.getString("summary"),
                                decode(result.getString("details_encoded")),
                                new ActorId(result.getString("actor_id")),
                                result.getTimestamp("occurred_at").toInstant()));
                    }
                }
            }
            return List.copyOf(output);
        });
    }

    private static String encode(Map<String, String> map) {
        StringBuilder builder = new StringBuilder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!builder.isEmpty()) builder.append('.');
            builder.append(encoder.encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8)))
                    .append(':')
                    .append(encoder.encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        return builder.toString();
    }

    private static Map<String, String> decode(String text) {
        if (text == null || text.isEmpty()) return Map.of();
        Base64.Decoder decoder = Base64.getUrlDecoder();
        LinkedHashMap<String, String> output = new LinkedHashMap<>();
        for (String pair : text.split("\\.")) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) throw new IllegalStateException("Malformed activity details");
            output.put(
                    new String(decoder.decode(parts[0]), StandardCharsets.UTF_8),
                    new String(decoder.decode(parts[1]), StandardCharsets.UTF_8));
        }
        return Map.copyOf(output);
    }
}
