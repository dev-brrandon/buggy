package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.repository.BuggyTaskRepository;
import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelation;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelationType;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcBuggyTaskRepository implements BuggyTaskRepository {
    private final TransactionRunner transactions;
    public JdbcBuggyTaskRepository(TransactionRunner transactions) { this.transactions = Objects.requireNonNull(transactions, "transactions"); }

    @Override public Optional<BuggyTaskRevision> findCurrent(BuggyTaskId taskId) {
        return transactions.readOnly(c -> findCurrent(c, Objects.requireNonNull(taskId, "taskId")));
    }
    @Override public List<BuggyTaskRevision> listCurrentTasks(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        return transactions.readOnly(c -> {
            ArrayList<BuggyTaskRevision> out = new ArrayList<>();
            try (PreparedStatement s=c.prepareStatement("SELECT r.* FROM assistant.buggy_task_head h JOIN assistant.buggy_task_revisions r ON r.assistant_id=h.assistant_id AND r.task_id=h.task_id AND r.task_revision=h.current_revision WHERE h.assistant_id='BUGGY' ORDER BY r.changed_at DESC,r.task_id LIMIT ?")) {
                s.setInt(1, limit); try(ResultSet r=s.executeQuery()){ while(r.next()) out.add(map(r)); }
            }
            return List.copyOf(out);
        });
    }
    @Override public List<BuggyTaskRevision> history(BuggyTaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return transactions.readOnly(c -> {
            ArrayList<BuggyTaskRevision> out = new ArrayList<>();
            try (PreparedStatement s=c.prepareStatement("SELECT * FROM assistant.buggy_task_revisions WHERE assistant_id='BUGGY' AND task_id=? ORDER BY task_revision")) {
                s.setObject(1, taskId.value()); try(ResultSet r=s.executeQuery()){ while(r.next()) out.add(map(r)); }
            }
            return List.copyOf(out);
        });
    }
    @Override public BuggyTaskRevision append(long expected, BuggyTaskRevision revision) {
        Objects.requireNonNull(revision, "revision");
        return transactions.required(c -> {
            lock(c, revision.taskId().value());
            long actual=findCurrent(c, revision.taskId()).map(BuggyTaskRevision::revision).orElse(0L);
            if(actual!=expected) throw new IllegalStateException("Stale Buggy task revision");
            try(PreparedStatement s=c.prepareStatement("INSERT INTO assistant.buggy_task_revisions(assistant_id,task_id,task_revision,previous_revision,title,description,status,attention,progress_percent,waiting_reason,changed_by,changed_at) VALUES ('BUGGY',?,?,?,?,?,?,?,?,?,?,?)")){
                s.setObject(1,revision.taskId().value()); s.setLong(2,revision.revision());
                if(revision.previousRevision()==null)s.setObject(3,null);else s.setLong(3,revision.previousRevision());
                s.setString(4,revision.title()); s.setString(5,revision.description()); s.setString(6,revision.status().name()); s.setString(7,revision.attention().name()); s.setInt(8,revision.progressPercent()); s.setString(9,revision.waitingReason()); s.setString(10,revision.changedBy().value()); s.setTimestamp(11,Timestamp.from(revision.changedAt())); one(s.executeUpdate());
            }
            if(expected==0){ try(PreparedStatement s=c.prepareStatement("INSERT INTO assistant.buggy_task_head(assistant_id,task_id,current_revision,updated_at) VALUES ('BUGGY',?,?,?)")){s.setObject(1,revision.taskId().value());s.setLong(2,revision.revision());s.setTimestamp(3,Timestamp.from(revision.changedAt()));one(s.executeUpdate());}}
            else { try(PreparedStatement s=c.prepareStatement("UPDATE assistant.buggy_task_head SET current_revision=?,updated_at=? WHERE assistant_id='BUGGY' AND task_id=? AND current_revision=?")){s.setLong(1,revision.revision());s.setTimestamp(2,Timestamp.from(revision.changedAt()));s.setObject(3,revision.taskId().value());s.setLong(4,expected);one(s.executeUpdate());}}
            return findCurrent(c,revision.taskId()).orElseThrow();
        });
    }
    @Override public void addRelation(BuggyTaskRelation relation) {
        Objects.requireNonNull(relation,"relation"); transactions.required(c->{try(PreparedStatement s=c.prepareStatement("INSERT INTO assistant.buggy_task_relations(assistant_id,source_task_id,target_task_id,relation_type,created_by,created_at) VALUES ('BUGGY',?,?,?,?,?)")){s.setObject(1,relation.sourceTaskId().value());s.setObject(2,relation.targetTaskId().value());s.setString(3,relation.relationType().name());s.setString(4,relation.createdBy().value());s.setTimestamp(5,Timestamp.from(relation.createdAt()));one(s.executeUpdate());}return null;});
    }
    @Override public List<BuggyTaskRelation> relations(BuggyTaskId taskId) {
        Objects.requireNonNull(taskId,"taskId"); return transactions.readOnly(c->{ArrayList<BuggyTaskRelation> out=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT source_task_id,target_task_id,relation_type,created_by,created_at FROM assistant.buggy_task_relations WHERE assistant_id='BUGGY' AND (source_task_id=? OR target_task_id=?) ORDER BY created_at")){s.setObject(1,taskId.value());s.setObject(2,taskId.value());try(ResultSet r=s.executeQuery()){while(r.next())out.add(new BuggyTaskRelation(new BuggyTaskId(r.getObject(1,UUID.class)),new BuggyTaskId(r.getObject(2,UUID.class)),BuggyTaskRelationType.valueOf(r.getString(3)),new ActorId(r.getString(4)),r.getTimestamp(5).toInstant()));}}return List.copyOf(out);});
    }
    private static Optional<BuggyTaskRevision> findCurrent(Connection c,BuggyTaskId id)throws SQLException{try(PreparedStatement s=c.prepareStatement("SELECT r.* FROM assistant.buggy_task_head h JOIN assistant.buggy_task_revisions r ON r.assistant_id=h.assistant_id AND r.task_id=h.task_id AND r.task_revision=h.current_revision WHERE h.assistant_id='BUGGY' AND h.task_id=?")){s.setObject(1,id.value());try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}}
    private static BuggyTaskRevision map(ResultSet r)throws SQLException{long p=r.getLong("previous_revision");Long prev=r.wasNull()?null:p;return new BuggyTaskRevision(new BuggyTaskId(r.getObject("task_id",UUID.class)),r.getLong("task_revision"),prev,r.getString("title"),r.getString("description"),BuggyTaskStatus.valueOf(r.getString("status")),BuggyAttentionLevel.valueOf(r.getString("attention")),r.getInt("progress_percent"),r.getString("waiting_reason"),new ActorId(r.getString("changed_by")),r.getTimestamp("changed_at").toInstant());}
    private static void lock(Connection c,UUID id)throws SQLException{try(PreparedStatement s=c.prepareStatement("SELECT pg_advisory_xact_lock(?)")){s.setLong(1,id.getMostSignificantBits()^id.getLeastSignificantBits()^0x4255474759423036L);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalStateException("advisory lock failed");}}}
    private static void one(int n){if(n!=1)throw new IllegalStateException("Expected one modified row");}
}
