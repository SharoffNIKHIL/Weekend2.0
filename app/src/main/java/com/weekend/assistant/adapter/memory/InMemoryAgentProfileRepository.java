package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.port.AgentProfileRepository;
import java.util.Comparator;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAgentProfileRepository extends InMemoryEntityRepository<AgentProfile> implements AgentProfileRepository {
    public InMemoryAgentProfileRepository() {
        super(AgentProfile::id, Comparator.comparing(AgentProfile::kind).thenComparing(AgentProfile::createdAt));
    }
}
