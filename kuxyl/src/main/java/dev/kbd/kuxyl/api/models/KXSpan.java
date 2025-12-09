package dev.kbd.kuxyl.api.models;

/**
 * Represents a span in the KX tracing system. A span acts as a node in an
 * execution graph
 * that captures both the hierarchy and the causal sequence of operations.
 * <p>
 * The structure conceptually forms a <strong>Tree with Causal Links</strong>:
 * <ul>
 * <li><strong>Hierarchy (Parent-Child):</strong> The {@code parentSpanId}
 * defines ownership.
 * If Span A triggers Span B, A is the parent. This forms a standard tree
 * structure.</li>
 * <li><strong>Sequence (Previous-Next):</strong> The {@code prevSpanId} defines
 * causality or order at the same level.
 * It points to the operation that completed explicitly before the current one
 * began.</li>
 * </ul>
 * <p>
 * <strong>Async & Concurrency (Branching):</strong><br>
 * Unlike a simple linked list, this model supports async forks. If an operation
 * spawns multiple
 * parallel tasks (e.g., A1 and A2) simultaneously:
 * <ul>
 * <li>Both A1 and A2 may point to the <em>same</em> {@code prevSpanId} (the
 * start of the scope or the last sync step).</li>
 * <li>This correctly represents a "fork" in the execution flow.</li>
 * </ul>
 * <p>
 * <strong>Example Structure:</strong>
 * 
 * <pre>
 * [Span Root]
 *  |
 *  +-- [Step 1] --next--> [Step 2] (Fork Point)
 *                          |
 *                          +--next--> [Async Task A]
 *                          |
 *                          +--next--> [Async Task B]
 * </pre>
 *
 * @param id                The unique identifier of this span.
 * @param prevSpanId        The ID of the span that causally preceded this one.
 *                          In async flows,
 *                          multiple spans may share the same prevSpanId,
 *                          indicating a fork.
 * @param parentSpanId      The ID of the parent span that contains this span.
 * @param message           A descriptive message or name for the operation.
 * @param localStartTime    Timestamp when the span object was
 *                          created/initiated.
 * @param localEnteredTime  Timestamp when the operation actually began
 *                          execution.
 * @param localFinishedTime Timestamp when the operation finished execution.
 * @param localReturnedTime Timestamp when the result was returned.
 */
public record KXSpan(String id,
                String prevSpanId,
                String parentSpanId,
                String message,
                long localStartTime,
                long localEnteredTime,
                long localFinishedTime,
                long localReturnedTime) {

}
