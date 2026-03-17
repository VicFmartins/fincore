package com.fincore.application.ports;

import java.util.List;

public interface OutboxRepositoryPort {
    void save(OutboxMessage message);

    void saveAll(List<OutboxMessage> messages);
}
