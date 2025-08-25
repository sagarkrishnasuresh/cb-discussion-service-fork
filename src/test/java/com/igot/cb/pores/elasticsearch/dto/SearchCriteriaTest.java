package com.igot.cb.pores.elasticsearch.dto;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SearchCriteriaTest {

    @Test
    void testEquals_sameObject() {
        SearchCriteria criteria = new SearchCriteria();
        assertEquals(criteria, criteria); // this == o
    }

    @Test
    void testEquals_nullObject() {
        SearchCriteria criteria = new SearchCriteria();
        assertNotEquals(null, criteria); // o == null
    }

    @Test
    void testEquals_differentClass() {
        SearchCriteria criteria = new SearchCriteria();
        assertNotEquals("Some String", criteria); // different class
    }

    @Test
    void testEquals_allFieldsEqual() {
        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put("key", "value");

        List<String> fields = Arrays.asList("field1", "field2");
        List<String> facets = Arrays.asList("facet1", "facet2");
        Map<String, Object> query = Map.of("q1", "v1");

        SearchCriteria c1 = new SearchCriteria(filterMap, fields, 1, 10, "orderBy", "asc", "search", facets, query);
        SearchCriteria c2 = new SearchCriteria(new HashMap<>(filterMap), new ArrayList<>(fields), 1, 10, "orderBy", "asc", "search", new ArrayList<>(facets), new HashMap<>(query));

        assertEquals(c1, c2); // all fields same
        assertEquals(c2, c1); // symmetric
    }

    @Test
    void testEquals_differentPageNumber() {
        SearchCriteria c1 = new SearchCriteria(null, null, 1, 10, "orderBy", "asc", "search", null, null);
        SearchCriteria c2 = new SearchCriteria(null, null, 2, 10, "orderBy", "asc", "search", null, null);

        assertNotEquals(c1, c2); // pageNumber differs
    }

    @Test
    void testEquals_differentPageSize() {
        SearchCriteria c1 = new SearchCriteria(null, null, 1, 10, "orderBy", "asc", "search", null, null);
        SearchCriteria c2 = new SearchCriteria(null, null, 1, 20, "orderBy", "asc", "search", null, null);

        assertNotEquals(c1, c2); // pageSize differs
    }

    @Test
    void testEquals_differentSearchString() {
        SearchCriteria c1 = new SearchCriteria(null, null, 1, 10, "orderBy", "asc", "search1", null, null);
        SearchCriteria c2 = new SearchCriteria(null, null, 1, 10, "orderBy", "asc", "search2", null, null);

        assertNotEquals(c1, c2); // searchString differs
    }

    @Test
    void testEquals_differentRequestedFields() {
        SearchCriteria c1 = new SearchCriteria(null, Arrays.asList("f1"), 1, 10, "orderBy", "asc", "search", null, null);
        SearchCriteria c2 = new SearchCriteria(null, Arrays.asList("f2"), 1, 10, "orderBy", "asc", "search", null, null);

        assertNotEquals(c1, c2); // requestedFields differs
    }

    @Test
    void testEquals_differentOrderBy() {
        SearchCriteria c1 = new SearchCriteria(null, null, 1, 10, "order1", "asc", "search", null, null);
        SearchCriteria c2 = new SearchCriteria(null, null, 1, 10, "order2", "asc", "search", null, null);

        assertNotEquals(c1, c2); // orderBy differs
    }

    @Test
    void testEquals_differentOrderDirection() {
        SearchCriteria c1 = new SearchCriteria(null, null, 1, 10, "orderBy", "asc", "search", null, null);
        SearchCriteria c2 = new SearchCriteria(null, null, 1, 10, "orderBy", "desc", "search", null, null);

        assertNotEquals(c1, c2); // orderDirection differs
    }

    @Test
    void testEquals_differentFacets() {
        SearchCriteria c1 = new SearchCriteria(null, null, 1, 10, "orderBy", "asc", "search", Arrays.asList("f1"), null);
        SearchCriteria c2 = new SearchCriteria(null, null, 1, 10, "orderBy", "asc", "search", Arrays.asList("f2"), null);

        assertNotEquals(c1, c2); // facets differ
    }

    @Test
    void testEquals_nullFieldsEqual() {
        SearchCriteria c1 = new SearchCriteria(null, null, 1, 10, null, null, null, null, null);
        SearchCriteria c2 = new SearchCriteria(null, null, 1, 10, null, null, null, null, null);

        assertEquals(c1, c2); // both null fields
    }
}
