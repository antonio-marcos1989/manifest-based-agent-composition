package com.br.honeycombframework.repository;

import com.br.honeycombframework.model.AgentExecutionLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class AgentExecutionLogQuery {

    private final MongoTemplate mongoTemplate;

    public AgentExecutionLogQuery(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<AgentExecutionLog> findRecentByAgentId(String agentId, int limit) {
        Query query = Query.query(Criteria.where("agentId").is(agentId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        return mongoTemplate.find(query, AgentExecutionLog.class);
    }

    public Optional<AgentExecutionLog> findLatestForGoalTaskAndAgent(
            String goalId,
            String taskId,
            String agentId) {
        return findLatestForGoalTaskAndAgent(goalId, taskId, agentId, null);
    }

    public Optional<AgentExecutionLog> findLatestForGoalTaskAndAgent(
            String goalId,
            String taskId,
            String agentId,
            String executionRunId) {
        Criteria criteria = Criteria.where("goalId").is(goalId)
                .and("taskId").is(taskId)
                .and("agentId").is(agentId);
        if (executionRunId != null && !executionRunId.isBlank()) {
            criteria = criteria.and("executionRunId").is(executionRunId);
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, AgentExecutionLog.class));
    }

    public List<AgentExecutionLog> findByExecutionRunIdOrderByTimestampAsc(String executionRunId) {
        Query query = Query.query(Criteria.where("executionRunId").is(executionRunId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, AgentExecutionLog.class);
    }

    public Optional<LocalDateTime> findLatestTimestampByAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("agentId").is(agentId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(1);
        query.fields().include("timestamp");
        AgentExecutionLog log = mongoTemplate.findOne(query, AgentExecutionLog.class);
        return Optional.ofNullable(log).map(AgentExecutionLog::getTimestamp);
    }

    public Map<String, LocalDateTime> findLatestTimestampByAgentIds(Collection<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Map.of();
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("agentId").in(agentIds)),
                Aggregation.sort(Sort.Direction.DESC, "timestamp"),
                Aggregation.group("agentId").first("timestamp").as("timestamp"));
        AggregationResults<AgentLatestExecution> results = mongoTemplate.aggregate(
                aggregation, AgentExecutionLog.class, AgentLatestExecution.class);
        return results.getMappedResults().stream()
                .filter(row -> row.getAgentId() != null && row.getTimestamp() != null)
                .collect(Collectors.toMap(AgentLatestExecution::getAgentId, AgentLatestExecution::getTimestamp));
    }

    private static final class AgentLatestExecution {
        @org.springframework.data.mongodb.core.mapping.Field("_id")
        private String agentId;
        private LocalDateTime timestamp;

        public String getAgentId() {
            return agentId;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}
