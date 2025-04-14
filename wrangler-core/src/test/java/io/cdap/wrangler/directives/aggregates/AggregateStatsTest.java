/*
 * Copyright © 2024 Your Name/Handle
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.wrangler.directives.aggregates;

import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.Row;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link AggregateStats}
 */
public class AggregateStatsTest {

    private static final double MB_DIVISOR = 1024.0 * 1024.0;
    private static final double NANO_TO_SEC_DIVISOR = 1_000_000_000.0;
    private static final double DELTA = 0.00001; // Tolerance for double comparisons

    @Test
    public void testSimpleAggregation() throws Exception {
        // Input Rows
        List<Row> rows = Arrays.asList(
                new Row("data_transfer_size", "1024 KB").add("response_time", "500 ms"),
                new Row("data_transfer_size", "2MB").add("response_time", "1.5 s"),
                new Row("data_transfer_size", "512kB").add("response_time", "250ms"),
                new Row("data_transfer_size", "0.5 MB").add("response_time", "0.75 s"),
                new Row("data_transfer_size", "invalid").add("response_time", "10s"), // Invalid size, should be skipped
                new Row("data_transfer_size", "1 MB").add("response_time", "invalid") // Invalid time, should be skipped
        );

        // Recipe
        String[] recipe = new String[]{
                "aggregate-stats :data_transfer_size :response_time :total_size_mb :total_time_sec"
        };

        // Execute
        List<Row> results = TestingRig.execute(recipe, rows);

        // --- Assertions ---
        Assert.assertEquals(1, results.size()); // Should produce a single aggregate row

        // Expected Calculation (Based on rows with valid pairs)
        // Valid sizes: 1024 KB, 2 MB, 512 KB, 0.5 MB
        // Valid times: 500 ms, 1.5 s, 250 ms, 0.75 s

        // Sizes in bytes: 1024*1024, 2*1024*1024, 512*1024, 0.5*1024*1024
        long totalBytes = (long) ((1.0 + 2.0 + 0.5 + 0.5) * 1024.0 * 1024.0);
        double expectedTotalMB = totalBytes / (1024.0 * 1024.0);

        // Times in nanoseconds: 500*1M, 1.5*1G, 250*1M, 0.75*1G
        long totalNanos = (long) (500 * 1_000_000L +
                                 1.5 * 1_000_000_000L +
                                 250 * 1_000_000L +
                                 0.75 * 1_000_000_000L);
        double expectedTotalSeconds = totalNanos / 1_000_000_000.0;

        // Check the output row
        Row outputRow = results.get(0);
        Assert.assertEquals(expectedTotalMB, (Double) outputRow.getValue("total_size_mb"), 0.001);
        Assert.assertEquals(expectedTotalSeconds, (Double) outputRow.getValue("total_time_sec"), 0.001);
    }

    @Test
    public void testBasicAggregation() throws Exception {
        String[] recipe = new String[]{
                "aggregate-stats :size :time total_mb total_sec"
        };

        List<Row> rows = Arrays.asList(
                new Row("size", "10KB").add("time", "500ms"),
                new Row("size", "2MB").add("time", "1s"),
                new Row("size", "1024B").add("time", "2500ms")
        );

        double expectedMb = (10.0 * 1024 + 2.0 * 1024 * 1024 + 1024) / MB_DIVISOR;
        double expectedSec = (500.0 * 1e6 + 1.0 * 1e9 + 2500.0 * 1e6) / NANO_TO_SEC_DIVISOR;

        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals("Should return a single aggregated row", 1, results.size());
        Row resultRow = results.get(0);

        Assert.assertEquals(expectedMb, (Double) resultRow.getValue("total_mb"), DELTA);
        Assert.assertEquals(expectedSec, (Double) resultRow.getValue("total_sec"), DELTA);
    }

    @Test
    public void testAggregationWithInvalidAndNulls() throws Exception {
         String[] recipe = new String[]{
                "aggregate-stats :size :time total_mb total_sec"
        };

        List<Row> rows = Arrays.asList(
                new Row("size", "1MB").add("time", "1s"),
                new Row("size", "invalid").add("time", "500ms"),
                new Row("size", "2MB").add("time", null),
                new Row("size", null).add("time", "2s"),
                new Row("size", "512KB").add("time", "invalid_time"),
                new Row("size", null).add("time", null)
        );

        double expectedBytes = (1.0 * 1024 * 1024) + (2.0 * 1024 * 1024) + (512.0 * 1024);
        double expectedNanos = (1.0 * 1e9) + (500.0 * 1e6) + (2.0 * 1e9);
        double expectedMb = expectedBytes / MB_DIVISOR;
        double expectedSec = expectedNanos / NANO_TO_SEC_DIVISOR;

        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals("Should return a single aggregated row", 1, results.size());
        Row resultRow = results.get(0);

        Assert.assertEquals(expectedMb, (Double) resultRow.getValue("total_mb"), DELTA);
        Assert.assertEquals(expectedSec, (Double) resultRow.getValue("total_sec"), DELTA);
    }

    @Test
    public void testAggregationNoRows() throws Exception {
        String[] recipe = new String[]{
                "aggregate-stats :size :time total_mb total_sec"
        };

        List<Row> rows = Arrays.asList();

        List<Row> results = TestingRig.execute(recipe, rows);

        // Should return empty list if no rows were processed
        Assert.assertTrue("Result list should be empty for empty input", results.isEmpty());
    }

     @Test
    public void testFractionalUnitsAggregation() throws Exception {
        String[] recipe = new String[]{
                "aggregate-stats :size :time total_mb total_sec"
        };

        List<Row> rows = Arrays.asList(
                new Row("size", "1.5MB").add("time", "2.5s"),
                new Row("size", "0.5GB").add("time", "0.1min")
        );

        double expectedBytes = (1.5 * 1024 * 1024) + (0.5 * 1024 * 1024 * 1024);
        double expectedNanos = (2.5 * 1e9) + (0.1 * 60 * 1e9);
        double expectedMb = expectedBytes / MB_DIVISOR;
        double expectedSec = expectedNanos / NANO_TO_SEC_DIVISOR;

        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals("Should return a single aggregated row", 1, results.size());
        Row resultRow = results.get(0);

        Assert.assertEquals(expectedMb, (Double) resultRow.getValue("total_mb"), DELTA);
        Assert.assertEquals(expectedSec, (Double) resultRow.getValue("total_sec"), DELTA);
    }
} 