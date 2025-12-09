# Tracing Model Analysis: Causal vs. Time-Based

This document analyzes the trade-offs between two primary models for representing trace data: **Causal (Structural/Parent-Child)** and **Time-Based**.

## 1. Causal Model (Parent-Child / Structural)

This model relies on explicit references between spans (`parentSpanId`, `prevSpanId`) to reconstruct the execution flow.

### Strengths
*   **Exact Reconstruction:** Can perfectly reconstruct the "Story" or call stack of a specific request, even if clocks are slightly askew.
*   **Causality:** Explicitly shows *why* something happened (A triggered B).
*   **Linear Logic:** The `prevSpanId` (sibling link) is excellent for visualized synchronous, linear flows (e.g., function A called B, then C).

### Weaknesses & Failure Modes
*   **Broken Context (The "Missing Link"):** If a middleware or external system drops the tracing headers, the chain is broken. `parentSpanId` is lost, and the downstream trace becomes an orphaned island, invisible to root-based traversals.
*   **The "God Span" (Hotspots):** A single long-running parent (e.g., a background worker) with millions of children creates a massive hotspot. Querying "all children of ID X" becomes a performance bottleneck.
*   **Sampling Gaps:** If a parent span is sampled out (dropped) but children are kept, the tree cannot be reconstructed.

## 2. Time-Based Model

This model relies on `localStartTime` and `duration` to index and retrieve spans, treating them as events on a timeline.

### Strengths
*   **Resilience:** Does not depend on explicit links. If headers are stripped, you can still find spans by querying the time range (e.g., "Show errors between 10:00:00 and 10:00:01").
*   **System-Wide Correlation:** Allows overlaying unrelated events. You can see that Service A was slow *at the same time* Service B was overloading the DB, even if they have no direct parent-child relationship.
*   **Scalability:** Time-series databases handle "infinite" streams of events efficiently by partitioning on time, avoiding the "God Span" read/write bottlenecks.

### Weaknesses
*   **Ambiguity:** In high-concurrency sub-millisecond systems, timestamps might not have enough resolution to prove order. Did A happen before B, or did they happen at the exact same nanosecond?
*   **No Explicit Cause:** It shows *coincidence* (happened at the same time), not necessarily *causality* (A triggered B).

## Recommendation: Hybrid Approach

For a robust tracing system (Kuxyl), we should **support both**:

1.  **Store Structural IDs (`parentSpanId`, `prevSpanId`):** Use these to build the visualization tree and causal graphs when data is complete.
2.  **Primary Index on Time (`localStartTime`):** ALWAYS index by time. This allows:
    *   Retrieving partial/broken traces.
    *   Analyzing system-wide performance windows.
    *   Handling massive fan-out scenarios without hotspots.

### Design Implication
The `KXSpan` model is correct to hold both structural fields and high-precision timestamps. The storage engine must optimize for Time-Range queries primarily, with ID lookups as a secondary access path.
