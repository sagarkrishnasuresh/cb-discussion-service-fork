package com.igot.cb.pores.elasticsearch.dto;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FacetDTOTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() {
        FacetDTO dto = new FacetDTO();
        dto.setValue("testValue");
        dto.setCount(123L);

        assertEquals("testValue", dto.getValue());
        assertEquals(123L, dto.getCount());
    }

    @Test
    void testAllArgsConstructor() {
        FacetDTO dto = new FacetDTO("value1", 456L);

        assertEquals("value1", dto.getValue());
        assertEquals(456L, dto.getCount());
    }
}
