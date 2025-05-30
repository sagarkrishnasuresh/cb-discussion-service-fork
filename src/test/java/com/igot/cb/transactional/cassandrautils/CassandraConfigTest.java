package com.igot.cb.transactional.cassandrautils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CassandraConfigTest {

    // Concrete subclass for testing
    static class CassandraConfigImpl extends CassandraConfig {
        // No additional methods needed
    }

    @Test
    void testGettersAndSetters() {
        CassandraConfigImpl config = new CassandraConfigImpl();

        // Test initial values are null/0
        assertNull(config.getContactPoints());
        assertEquals(0, config.getPort());
        assertNull(config.getKeyspaceName());

        // Set values
        config.setContactPoints("127.0.0.1");
        config.setPort(9042);
        config.setKeyspaceName("test_keyspace");

        // Test getters return what was set
        assertEquals("127.0.0.1", config.getContactPoints());
        assertEquals(9042, config.getPort());
        assertEquals("test_keyspace", config.getKeyspaceName());
    }
}
