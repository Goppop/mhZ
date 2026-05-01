package com.policyradar.sources.html.selector;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SelectorCandidate {
    private String selector;
    private int itemCount;
    private double confidence;
    private String regionContainerSelector;
    private List<int[]> matchedIndexPaths;
    private String strategy;
}
