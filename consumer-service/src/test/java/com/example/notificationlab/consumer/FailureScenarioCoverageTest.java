package com.example.notificationlab.consumer;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-10.2 guard — ensures the nine scenario IDs stay registered for Phase 10 evidence.
 * Human-readable matrix: {@code notes/phase-10-failure-scenarios.md} at repo root.
 */
class FailureScenarioCoverageTest {

    @Test
    void failureScenarioIdListHasNineEntries() throws Exception {
        List<Integer> ids = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/failure-scenario-ids.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                ids.add(Integer.parseInt(line));
            }
        }
        assertThat(ids).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }
}