package com.br.honeycombframework.repository;

import com.br.honeycombframework.model.AgentExecutionLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentExecutionLogRepository extends MongoRepository<AgentExecutionLog, String> {
}
