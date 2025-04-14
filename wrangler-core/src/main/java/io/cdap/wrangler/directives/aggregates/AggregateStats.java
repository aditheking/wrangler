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

import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.FinalisingDirective;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.annotations.Description;
import io.cdap.wrangler.api.annotations.Name;
import io.cdap.wrangler.api.annotations.Usage;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.TransientVariableScope;

import java.util.ArrayList;
import java.util.List;

/**
 * A directive that aggregates byte sizes and time durations from specified columns.
 */
@Name("aggregate-stats")
@Categories(categories = {"aggregate"})
@Description("Aggregates total byte size and total time duration from specified columns.")
@Usage("aggregate-stats :size_column :time_column :target_size_total :target_time_total")
public class AggregateStats implements FinalisingDirective {

    public static final String AGG_TOTAL_BYTES = "aggregate-stats.totalBytes";
    public static final String AGG_TOTAL_NANOS = "aggregate-stats.totalNanos";
    public static final String AGG_COUNT = "aggregate-stats.count";
    public static final String FINAL_RESULT_KEY = "_directive.result";

    private String sizeCol;
    private String timeCol;
    private String targetSizeCol;
    private String targetTimeCol;

    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder("aggregate-stats");
        builder.define("size_column", TokenType.COLUMN_NAME);
        builder.define("time_column", TokenType.COLUMN_NAME);
        builder.define("target_size_total", TokenType.COLUMN_NAME);
        builder.define("target_time_total", TokenType.COLUMN_NAME);
        // TODO: Add optional arguments for output units (e.g., MB, s) and aggregation type (total/average)
        return builder.build();
    }

    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        this.sizeCol = ((ColumnName) args.value("size_column")).value();
        this.timeCol = ((ColumnName) args.value("time_column")).value();
        this.targetSizeCol = ((ColumnName) args.value("target_size_total")).value();
        this.targetTimeCol = ((ColumnName) args.value("target_time_total")).value();
    }

    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        TransientStore store = context.getTransientStore();

        // Get current aggregates or initialize if null
        long currentBytes = store.<Long>get(AGG_TOTAL_BYTES) == null ? 0L : store.<Long>get(AGG_TOTAL_BYTES);
        long currentNanos = store.<Long>get(AGG_TOTAL_NANOS) == null ? 0L : store.<Long>get(AGG_TOTAL_NANOS);
        long currentCount = store.<Long>get(AGG_COUNT) == null ? 0L : store.<Long>get(AGG_COUNT);

        for (Row row : rows) {
            Object sizeVal = row.getValue(sizeCol);
            Object timeVal = row.getValue(timeCol);

            // Process only if both values are non-null Strings
            if (sizeVal instanceof String && timeVal instanceof String) {
                try {
                    ByteSize size = new ByteSize((String) sizeVal);
                    TimeDuration duration = new TimeDuration((String) timeVal);

                    currentBytes += size.getBytes();
                    currentNanos += duration.getNanoseconds();
                    currentCount++;

                } catch (IllegalArgumentException e) {
                    // Skip row if parsing fails (e.g., invalid format)
                    // Optional: Add logging using context.getTracer() or context.getMetrics()
                    // context.getMetrics().count("aggregate-stats.parse.errors", 1);
                } catch (Exception e) {
                   // Catch unexpected errors during processing of a row
                   throw new DirectiveExecutionException("aggregate-stats",
                                                     String.format("Unexpected error processing row: %s", e.getMessage()), e);
                }
            } // Optional: else if (sizeVal != null || timeVal != null) { log warning about non-string type? }
        }

        // Update store with new totals
        store.set(TransientVariableScope.GLOBAL, AGG_TOTAL_BYTES, currentBytes);
        store.set(TransientVariableScope.GLOBAL, AGG_TOTAL_NANOS, currentNanos);
        store.set(TransientVariableScope.GLOBAL, AGG_COUNT, currentCount);

        // Aggregation directives return empty list during execute phase
        return new ArrayList<>();
    }

    @Override
    public List<Row> finish(ExecutorContext context) {
        TransientStore store = context.getTransientStore();

        // Retrieve final values from the store, defaulting to 0 if null
        long totalBytes = store.<Long>get(AGG_TOTAL_BYTES) == null ? 0L : store.<Long>get(AGG_TOTAL_BYTES);
        long totalNanos = store.<Long>get(AGG_TOTAL_NANOS) == null ? 0L : store.<Long>get(AGG_TOTAL_NANOS);
        long count = store.<Long>get(AGG_COUNT) == null ? 0L : store.<Long>get(AGG_COUNT);

        List<Row> finalResult = new ArrayList<>();
        if (count > 0) {
            // --- Unit Conversions (Example: Bytes to MB, Nanos to Seconds) ---
            // TODO: Make units configurable via directive arguments
            double totalMB = (double) totalBytes / (1024.0 * 1024.0); // Using 1024^2 for MB
            double totalSeconds = (double) totalNanos / 1_000_000_000.0;

            // Create the single result row
            Row resultRow = new Row();
            resultRow.add(targetSizeCol, totalMB); // Storing as double MB
            resultRow.add(targetTimeCol, totalSeconds); // Storing as double Seconds
            finalResult.add(resultRow);
        }
        // else: if count is 0, finalResult remains empty, which is correct.

        // FinalisingDirectives return the final result directly
        return finalResult;
    }

    @Override
    public void destroy() {
        // No resources to clean up
    }
} 