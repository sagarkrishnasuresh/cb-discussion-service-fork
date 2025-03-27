package com.igot.cb.pores.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SearchCriteria {

    private HashMap<String, Object> filterCriteriaMap;

    private List<String> requestedFields;

    private int pageNumber;

    private int pageSize;

    private String orderBy;

    private String orderDirection;

    private String searchString;

    private List<String> facets;

    private Map<String, Object> query;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchCriteria that = (SearchCriteria) o;
        return pageNumber == that.pageNumber &&
                pageSize == that.pageSize &&
                Objects.equals(searchString, that.searchString) &&
                Objects.equals(filterCriteriaMap, that.filterCriteriaMap) &&
                Objects.equals(requestedFields, that.requestedFields) &&
                Objects.equals(orderBy, that.orderBy) &&
                Objects.equals(orderDirection, that.orderDirection) &&
                Objects.equals(facets, that.facets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchString, filterCriteriaMap, requestedFields, pageNumber, pageSize, orderBy, orderDirection, facets);
    }
}
