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

import io.cdap.wrangler.api.Directive;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Usage;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.TransientVariableScope;

import java.util.List;
import java.util.ArrayList;

/**
 * A directive that aggregates byte size and time duration columns.
 */
@Name(AggregateStats.NAME)
@Description("Aggregates byte size and time duration columns, calculating total size and total time.")
@Usage(AggregateStats.USAGE)
public class AggregateStats implements Directive {

    public static final String NAME = "aggregate-stats";
    private static final String ARG_SIZE_COL = "size_col";
    private static final String ARG_TIME_COL = "time_col";
    private static final String ARG_TARGET_SIZE_COL = "target_size_col";
    private static final String ARG_TARGET_TIME_COL = "target_time_col";
    public static final String USAGE = NAME + " :" + ARG_SIZE_COL + " :" + ARG_TIME_COL + " :" + ARG_TARGET_SIZE_COL + " :" + ARG_TARGET_TIME_COL;

    private String sizeCol;
    private String timeCol;
    private String targetSizeCol;
    private String targetTimeCol;

    // Keys for TransientStore
    private static final String TOTAL_BYTES_KEY = NAME + ".total.bytes";
    private static final String TOTAL_NANOS_KEY = NAME + ".total.nanos";
    private static final String ROW_COUNT_KEY = NAME + ".row.count";
    // Key to store final result list in TransientStore (Framework might look for this)
    private static final String FINAL_RESULT_KEY = NAME + ".final.result";

    // Need context in destroy to access store
    private transient ExecutorContext context = null;

    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
        builder.define(ARG_SIZE_COL, TokenType.COLUMN_NAME);
        builder.define(ARG_TIME_COL, TokenType.COLUMN_NAME);
        builder.define(ARG_TARGET_SIZE_COL, TokenType.COLUMN_NAME);
        builder.define(ARG_TARGET_TIME_COL, TokenType.COLUMN_NAME);
        return builder.build();
    }

    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        this.sizeCol = ((ColumnName) args.value(ARG_SIZE_COL)).value();
        this.timeCol = ((ColumnName) args.value(ARG_TIME_COL)).value();
        this.targetSizeCol = ((ColumnName) args.value(ARG_TARGET_SIZE_COL)).value();
        this.targetTimeCol = ((ColumnName) args.value(ARG_TARGET_TIME_COL)).value();
        // Reset state in case of reuse
        this.context = null;
    }

    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        // Store context for use in destroy()
        if (this.context == null) {
            this.context = context;
        }
        TransientStore store = context.getTransientStore();

        for (Row row : rows) {
            Object sizeValue = row.getValue(sizeCol);
            Object timeValue = row.getValue(timeCol);
            boolean rowProcessed = false;

            if (sizeValue instanceof ByteSize) {
                Long currentBytes = store.<Long>get(TOTAL_BYTES_KEY);
                currentBytes = (currentBytes == null) ? 0L : currentBytes;
                store.set(TransientVariableScope.GLOBAL, TOTAL_BYTES_KEY, currentBytes + ((ByteSize) sizeValue).getBytes());
                rowProcessed = true;
            } else if (sizeValue != null) {
                 throw new DirectiveExecutionException(NAME, String.format("Column '%s' contained unexpected type '%s'. Expected ByteSize.",
                                                        sizeCol, sizeValue.getClass().getName()), new IllegalArgumentException());
            }

            if (timeValue instanceof TimeDuration) {
                Long currentNanos = store.<Long>get(TOTAL_NANOS_KEY);
                currentNanos = (currentNanos == null) ? 0L : currentNanos;
                store.set(TransientVariableScope.GLOBAL, TOTAL_NANOS_KEY, currentNanos + ((TimeDuration) timeValue).getNanos());
                if (rowProcessed) {
                     Long count = store.<Long>get(ROW_COUNT_KEY);
                     count = (count == null) ? 0L : count;
                     store.set(TransientVariableScope.GLOBAL, ROW_COUNT_KEY, count + 1);
                }
            } else if (timeValue != null) {
                 throw new DirectiveExecutionException(NAME, String.format("Column '%s' contained unexpected type '%s'. Expected TimeDuration.",
                                                        timeCol, timeValue.getClass().getName()), new IllegalArgumentException());
            }
        }
        return new ArrayList<>(); // Return empty list during execution
    }

    @Override
    public void destroy() {
        // Final aggregation happens here
        if (this.context != null) {
             TransientStore store = this.context.getTransientStore();
             Long totalBytes = store.<Long>get(TOTAL_BYTES_KEY);
             Long totalNanos = store.<Long>get(TOTAL_NANOS_KEY);
             Long rowCount = store.<Long>get(ROW_COUNT_KEY);

             totalBytes = (totalBytes == null) ? 0L : totalBytes;
             totalNanos = (totalNanos == null) ? 0L : totalNanos;
             rowCount = (rowCount == null) ? 0L : rowCount;

             List<Row> result = new ArrayList<>();
             if (rowCount > 0) {
                 Row outputRow = new Row();
                 // TODO: Unit conversion
                 outputRow.add(targetSizeCol, (double) totalBytes);
                 outputRow.add(targetTimeCol, (double) totalNanos / 1_000_000_000.0);
                 result.add(outputRow);
             }
             // Store the final result list in the transient store - hoping the framework picks it up
             store.set(TransientVariableScope.GLOBAL, FINAL_RESULT_KEY, result);
        } // else: context was null, cannot produce final result (should not happen in normal flow)

        // Clean up context reference
        this.context = null;
    }
} 