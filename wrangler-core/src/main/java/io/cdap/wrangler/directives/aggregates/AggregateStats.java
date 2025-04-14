package io.cdap.wrangler.directives.aggregates;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Optional;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.TransientVariableScope;
import io.cdap.wrangler.api.annotations.Usage;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.api.ErrorRowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A directive that aggregates byte size and time duration columns, calculating totals or averages.
 */
@Name(AggregateStats.NAME)
@Description("Aggregates byte size and time duration columns, calculating total or average size and time. Allows specifying output units.")
@Usage(AggregateStats.USAGE)
public class AggregateStats implements Directive {

    private static final Logger LOG = LoggerFactory.getLogger(AggregateStats.class);

    public static final String NAME = "aggregate-stats";
    // Argument Names
    private static final String ARG_SIZE_COL = "size_col";
    private static final String ARG_TIME_COL = "time_col";
    private static final String ARG_TARGET_SIZE_COL = "target_size_col";
    private static final String ARG_TARGET_TIME_COL = "target_time_col";
    private static final String ARG_AGG_TYPE = "aggregation_type"; // Optional: 'total' (default) or 'average'
    private static final String ARG_SIZE_UNIT = "size_unit";       // Optional: e.g., 'B', 'KB', 'MB', 'GB' (default: B)
    private static final String ARG_TIME_UNIT = "time_unit";       // Optional: e.g., 'ns', 'ms', 's', 'm', 'h', 'd' (default: s)

    public static final String USAGE =
            NAME + " :" + ARG_SIZE_COL + " :" + ARG_TIME_COL + " :" + ARG_TARGET_SIZE_COL + " :" + ARG_TARGET_TIME_COL +
            " [" + ARG_AGG_TYPE + ":<'total'|'average'>]" +
            " [" + ARG_SIZE_UNIT + ":<'B'|'KB'|'MB'|'GB'|'TB'|'PB'>]" +
            " [" + ARG_TIME_UNIT + ":<'ns'|'ms'|'s'|'m'|'h'|'d'>]";

    private String sizeCol;
    private String timeCol;
    private String targetSizeCol;
    private String targetTimeCol;
    private String aggType = "total"; // Default aggregation type
    private String sizeUnit = "B";    // Default output size unit
    private String timeUnit = "s";    // Default output time unit

    // Store Keys
    private static final String TOTAL_BYTES_KEY = NAME + ".total.bytes";
    private static final String TOTAL_NANOS_KEY = NAME + ".total.nanos";
    private static final String ROW_COUNT_KEY = NAME + ".row.count";
    private static final String FINAL_RESULT_KEY = NAME + ".final.result";

    private transient ExecutorContext context = null;

    // Size constants
    private static final long KB_IN_BYTES = 1024L;
    private static final long MB_IN_BYTES = 1024L * KB_IN_BYTES;
    private static final long GB_IN_BYTES = 1024L * MB_IN_BYTES;
    private static final long TB_IN_BYTES = 1024L * GB_IN_BYTES;
    private static final long PB_IN_BYTES = 1024L * TB_IN_BYTES;

    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
        builder.define(ARG_SIZE_COL, TokenType.COLUMN_NAME);
        builder.define(ARG_TIME_COL, TokenType.COLUMN_NAME);
        builder.define(ARG_TARGET_SIZE_COL, TokenType.COLUMN_NAME);
        builder.define(ARG_TARGET_TIME_COL, TokenType.COLUMN_NAME);
        builder.define(ARG_AGG_TYPE, TokenType.TEXT, Optional.TRUE); // Optional aggregation type
        builder.define(ARG_SIZE_UNIT, TokenType.TEXT, Optional.TRUE); // Optional size unit
        builder.define(ARG_TIME_UNIT, TokenType.TEXT, Optional.TRUE); // Optional time unit
        return builder.build();
    }

    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        this.sizeCol = ((ColumnName) args.value(ARG_SIZE_COL)).value();
        this.timeCol = ((ColumnName) args.value(ARG_TIME_COL)).value();
        this.targetSizeCol = ((ColumnName) args.value(ARG_TARGET_SIZE_COL)).value();
        this.targetTimeCol = ((ColumnName) args.value(ARG_TARGET_TIME_COL)).value();

        if (args.contains(ARG_AGG_TYPE)) {
            this.aggType = ((Text) args.value(ARG_AGG_TYPE)).value().toLowerCase();
            if (!this.aggType.equals("total") && !this.aggType.equals("average")) {
                throw new DirectiveParseException(NAME, String.format("Invalid %s: '%s'. Must be 'total' or 'average'.", ARG_AGG_TYPE, this.aggType));
            }
        }
        if (args.contains(ARG_SIZE_UNIT)) {
            this.sizeUnit = ((Text) args.value(ARG_SIZE_UNIT)).value().toUpperCase();
            if (!Arrays.asList("B", "KB", "MB", "GB", "TB", "PB").contains(this.sizeUnit)) {
                 throw new DirectiveParseException(NAME, String.format("Invalid %s: '%s'. Supported units: B, KB, MB, GB, TB, PB.", ARG_SIZE_UNIT, this.sizeUnit));
            }
        }
        if (args.contains(ARG_TIME_UNIT)) {
            this.timeUnit = ((Text) args.value(ARG_TIME_UNIT)).value().toLowerCase();
            if (!Arrays.asList("ns", "ms", "s", "m", "h", "d").contains(this.timeUnit)) {
                 throw new DirectiveParseException(NAME, String.format("Invalid %s: '%s'. Supported units: ns, ms, s, m, h, d.", ARG_TIME_UNIT, this.timeUnit));
            }
        }

        this.context = null;
    }

    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        if (this.context == null) {
            this.context = context;
        }
        TransientStore store = context.getTransientStore();

        for (Row row : rows) {
            Object sizeValue = row.getValue(sizeCol);
            Object timeValue = row.getValue(timeCol);
            boolean sizeProcessed = false;
            boolean timeProcessed = false;
            boolean rowSkipped = false;

            // Process Size
            try {
                if (sizeValue instanceof ByteSize) {
                    Long currentBytes = store.<Long>get(TOTAL_BYTES_KEY);
                    currentBytes = (currentBytes == null) ? 0L : currentBytes;
                    store.set(TransientVariableScope.GLOBAL, TOTAL_BYTES_KEY, currentBytes + ((ByteSize) sizeValue).getBytes());
                    sizeProcessed = true;
                } else if (sizeValue != null) {
                    // Log warning and skip this value if type is wrong
                    LOG.warn("Skipping size value for row (id: {}). Column '{}' contained unexpected type '{}'. Expected ByteSize.",
                             row.getValue("id"), sizeCol, sizeValue.getClass().getName()); // Assuming 'id' column exists for logging
                    context.getMetrics().count(NAME + ".skipped.size.values", 1);
                    rowSkipped = true; // Mark row as potentially incomplete
                }
            } catch (Exception e) {
                // Catch unexpected errors during size processing
                 LOG.warn("Error processing size value for row (id: {}), column '{}': {}. Skipping size value.",
                           row.getValue("id"), sizeCol, e.getMessage());
                 context.getMetrics().count(NAME + ".error.size.values", 1);
                 rowSkipped = true;
            }

            // Process Time
             try {
                if (timeValue instanceof TimeDuration) {
                    Long currentNanos = store.<Long>get(TOTAL_NANOS_KEY);
                    currentNanos = (currentNanos == null) ? 0L : currentNanos;
                    store.set(TransientVariableScope.GLOBAL, TOTAL_NANOS_KEY, currentNanos + ((TimeDuration) timeValue).getNanos());
                    timeProcessed = true;
                } else if (timeValue != null) {
                    // Log warning and skip this value if type is wrong
                    LOG.warn("Skipping time value for row (id: {}). Column '{}' contained unexpected type '{}'. Expected TimeDuration.",
                             row.getValue("id"), timeCol, timeValue.getClass().getName());
                    context.getMetrics().count(NAME + ".skipped.time.values", 1);
                    rowSkipped = true; // Mark row as potentially incomplete
                }
            } catch (Exception e) {
                 // Catch unexpected errors during time processing
                 LOG.warn("Error processing time value for row (id: {}), column '{}': {}. Skipping time value.",
                           row.getValue("id"), timeCol, e.getMessage());
                 context.getMetrics().count(NAME + ".error.time.values", 1);
                 rowSkipped = true;
            }

            // Increment row count only if both size and time were validly processed for this row
            // AND the row wasn't marked for skipping due to other errors/type issues.
            if (sizeProcessed && timeProcessed && !rowSkipped) {
                Long count = store.<Long>get(ROW_COUNT_KEY);
                count = (count == null) ? 0L : count;
                store.set(TransientVariableScope.GLOBAL, ROW_COUNT_KEY, count + 1);
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void destroy() {
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
                double finalSize = (double) totalBytes;
                double finalTime = (double) totalNanos;

                if (aggType.equals("average")) {
                    finalSize = finalSize / rowCount;
                    finalTime = finalTime / rowCount;
                }

                // Convert units
                finalSize = convertBytes(finalSize, sizeUnit);
                finalTime = convertNanos(finalTime, timeUnit);

                Row outputRow = new Row();
                outputRow.add(targetSizeCol, finalSize);
                outputRow.add(targetTimeCol, finalTime);
                result.add(outputRow);
            }
            store.set(TransientVariableScope.GLOBAL, FINAL_RESULT_KEY, result);
        }
        this.context = null;
    }

    private double convertBytes(double bytes, String targetUnit) {
        switch (targetUnit) {
            case "KB": return bytes / KB_IN_BYTES;
            case "MB": return bytes / MB_IN_BYTES;
            case "GB": return bytes / GB_IN_BYTES;
            case "TB": return bytes / TB_IN_BYTES;
            case "PB": return bytes / PB_IN_BYTES;
            case "B":
            default: return bytes;
        }
    }

    private double convertNanos(double nanos, String targetUnit) {
        switch (targetUnit) {
            case "ms": return nanos / TimeUnit.MILLISECONDS.toNanos(1);
            case "s":  return nanos / TimeUnit.SECONDS.toNanos(1);
            case "m":  return nanos / TimeUnit.MINUTES.toNanos(1);
            case "h":  return nanos / TimeUnit.HOURS.toNanos(1);
            case "d":  return nanos / TimeUnit.DAYS.toNanos(1);
            case "ns":
            default: return nanos;
        }
    }
}