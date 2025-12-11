Here is the clean, implementation-ready design document.

-----

# Xylem Architecture: Scalable "Infinite Zoom" Graph

## 1\. The Vision: What We Are Building

We are building an observability tool that functions as a **"Google Maps for Code."**

### Core Requirements

1.  **Semantic Zoom ("The Summary"):**

      * **Goal:** View a trace at a high level and see "Major Landmarks" (e.g., Endpoint $\to$ DB Call) connected directly.
      * **Constraint:** "Noise" (helper functions, wrappers) must be hidden without breaking the visual connectivity of the graph.
      * *Visual:* `Foo() -> ServiceCall` (hiding the intermediate layers).

2.  **Progressive Zoom ("The Deep Dive"):**

      * **Goal:** As the user zooms in, the graph must "slide open" to reveal the intermediate layers one by one.
      * *Visual:* `Foo() -> Helper() -> Wrapper() -> ServiceCall`.

3.  **Infinite Scalability:**

      * **Constraint:** Must handle traces with **100,000+ stack depth** (e.g., recursive loops).
      * **Constraint:** Must support millions of logs without write-latency penalties.

-----

## 2\. The Solution: "Hybrid Skip-Link" Architecture

To achieve infinite depth with $O(1)$ query performance, we utilize a hybrid data structure combining **Skip Lists** (for precise depth navigation) and **Root Anchors** (for instant high-level summaries).

### A. The Data Model (Enriched Storage)

We do not store the full ancestry path (which bloats storage). Instead, a background worker processes raw logs and writes to an optimized `enriched_spans` table.

**Table Schema:** `enriched_spans`

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique ID of the span. |
| `shows_at_zoom` | Int | **Semantic Filter.** <br> `0`: Root / Critical Errors<br> `1`: Major IO / Network Calls<br> `2`: Internal Logic / Noise |
| `depth` | Int | **Absolute Position.** The physical stack depth (e.g., 0, 1, 50, 100). |
| **`root_id`** | UUID | **The Anchor.** A direct link to the Trace Root (Depth 0). <br> *Purpose: Allows instant rendering of the "Architecture View".* |
| **`skip_links`** | Array | **The Navigation.** A compressed array of ancestors at exponential distances ($2^n$). <br> *Purpose: Allows "Progressive Zoom" to any depth.* |

### B. The Logic Flow

#### 1\. Ingestion (The Worker)

  * **Input:** Raw flat stream of spans.
  * **Process:**
      * Constructs the Tree in memory.
      * Calculates `depth` and `shows_at_zoom` based on rules (e.g., Duration \> 100ms = Major).
      * Generates `skip_links` by copying specific pointers from the parent's `skip_links` array.
  * **Output:** Bulk writes to a Columnar Database (e.g., ClickHouse).

#### 2\. Reading (The UI)

  * **Zoom Out:** Query `SELECT ... WHERE shows_at_zoom <= 1`.
      * UI uses `root_id` to draw connections.
  * **Zoom In:** Query `SELECT ... WHERE depth <= X`.
      * UI uses `skip_links` to mathematically find the nearest visible parent.

-----

## 3\. Concrete Example: The Walkthrough

**Scenario:** A request goes 3 layers deep.

  * **Depth 0:** `foo()` (Root)
  * **Depth 1:** `b()` (Internal Logic)
  * **Depth 2:** `ad()` (Wrapper)
  * **Depth 3:** `svc()` (Major IO Call)

### Step 1: The Stored Data

The Worker processes the trace and stores the following. Note that `skip_links` only stores powers of 2 ($2^0, 2^1, 2^2...$).

| Span | Zoom Lvl | Depth | `root_id` | `skip_links` (Distance 1, 2, 4...) |
| :--- | :--- | :--- | :--- | :--- |
| **foo** | 0 | 0 | `NULL` | `[]` |
| **b** | 2 | 1 | `foo` | `[foo]` (Dist 1) |
| **ad** | 2 | 2 | `foo` | `[b, foo]` (Dist 1, Dist 2) |
| **svc** | **1** | 3 | `foo` | `[ad, b]` (Dist 1, Dist 2) |

-----

### Step 2: Architecture Mode (High Level)

**User Query:** *"Show me the important stuff."*
`SELECT * FROM enriched_spans WHERE shows_at_zoom <= 1`

  * **Fetched:** `foo` (Depth 0), `svc` (Depth 3).
  * **The Visualization Problem:** We must connect `svc` to `foo`.
      * `svc` (Depth 3) has skip links for Dist 1 (`ad`) and Dist 2 (`b`). It does **not** have a direct skip link to Dist 3.
  * **The Solution:** `svc` checks its **`root_id`**.
      * Value is `foo`.
      * Is `foo` in the fetched list? **Yes.**
      * **Action:** Draw line `foo` $\to$ `svc`.

**Visual Result:**

```text
[ -------------------------- foo() -------------------------- ]
              |
              +---> [ serviceCall ]
```

-----

### Step 3: Logic Mode (Zoom Depth 1)

**User Query:** *"Expand the first layer of logic."*
`SELECT * FROM enriched_spans WHERE depth <= 1 OR shows_at_zoom <= 1`

  * **Fetched:** `foo` (Depth 0), `b` (Depth 1), `svc` (Depth 3).
  * **The Visualization Problem:** `svc` needs to connect to the nearest visible ancestor (`b`).
  * **The Solution:** `svc` calculates the gap.
      * Target Depth: 1. Current Depth: 3.
      * Distance Needed: 2 steps.
      * Is 2 a Power of 2? **Yes.**
      * **Action:** Use `skip_links[1]`. It points to `b`.
      * **Action:** Draw line `b` $\to$ `svc`.

**Visual Result:**

```text
[ -------------------------- foo() -------------------------- ]
      |
      +---> [ ----------- b() ----------- ]
                  |
                  +---> [ serviceCall ]
```

*(The graph "slides open." `svc` moves down to sit inside `b`.)*

-----

### Step 4: Full Debug (Zoom Depth 2)

**User Query:** *"Show me everything."*

  * **Fetched:** `foo`, `b`, `ad`, `svc`.
  * **The Solution:** `svc` connects to `ad` (Depth 2).
      * Distance Needed: 1 step.
      * **Action:** Use `skip_links[0]`. It points to `ad`.
      * **Action:** Draw line `ad` $\to$ `svc`.

**Visual Result:**

```text
[ -------------------------- foo() -------------------------- ]
      |
      +---> [ ----------- b() ----------- ]
                  |
                  +---> [ --- ad() --- ]
                            |
                            +---> [ svc ]
```

-----

## 4\. Scalability Analysis

Why does this survive production scale?

| Feature | The Scalability Factor |
| :--- | :--- |
| **Storage (Infinite Depth)** | **Skip Lists.** Even if a trace is 100,000 layers deep, the `skip_links` array only contains \~17 items (Logarithmic growth). We never store massive ancestry arrays. |
| **Write Throughput** | **Worker Pattern.** The ingestion API is decoupled from processing. The worker performs bulk inserts into ClickHouse, ensuring millions of spans/sec ingestion. |
| **Read Latency** | **Indexed Filtering.** The "High Level View" query leverages database indexes to only read the "Major" rows. Loading a summary of a 1M span trace takes milliseconds. |
| **Client Performance** | **O(1) Linking.** The browser never has to traverse the tree. It simply looks up `root_id` or a specific index in `skip_links` to find where to draw the line. |

-----

### Would you like to proceed with setting up the ClickHouse schema for the `enriched_spans` table?