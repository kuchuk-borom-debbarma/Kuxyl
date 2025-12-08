
### The Hybrid Database Model
Kuxyl utilizes a "Best Tool for the Job" database strategy, splitting relational user data from high-volume telemetry data.

| Component | Database | Reason |
| :--- | :--- | :--- |
| **Identity & Access** | **PostgreSQL** | ACID compliance, relational integrity for Users, Tenants, and Auth. |
| **Telemetry Storage** | **ClickHouse** | Column-oriented storage optimized for massive write throughput and analytical queries. |

---

## Deep Dive: Why ClickHouse?

Kuxyl uses **ClickHouse** as the primary engine for storing logs and execution traces. Unlike traditional row-based databases (MySQL/Postgres) or Key-Value stores (Cassandra), ClickHouse is a **Column-Oriented OLAP (Online Analytical Processing) database**.

### Key Technical Advantages for Kuxyl:
1.  **Compression & Storage Efficiency:**
    * Logs are text-heavy. ClickHouse stores data by column (e.g., all `timestamps` together, all `messages` together).
    * This allows for extreme compression ratios (often 10:1), significantly reducing storage costs compared to Elasticsearch or MongoDB.

2.  **Aggregation Speed:**
    * Observability requires answering questions like *"What is the 99th percentile latency?"* or *"Count error rates over the last hour."*
    * ClickHouse calculates these aggregations over millions of rows in milliseconds because it only reads the relevant columns, ignoring unrelated data.

3.  **Write Throughput:**
    * ClickHouse is designed to accept data in large batches, making it capable of ingesting millions of log lines per second on moderate hardware.

---

## Future Roadmap: Asynchronous Ingestion

As Kuxyl scales to handle millions of requests per second, writing directly to the database from the API Server will become a bottleneck. To ensure resilience and handle traffic spikes (backpressure), we plan to introduce an **Event Queue Architecture**.

### Planned Architecture: The "Buffer" Layer
We will decouple the *reception* of logs from the *storage* of logs using a message broker.

1.  **Ingestion:** The API Server accepts logs and immediately pushes them to a high-throughput queue (**Apache Kafka** or **RabbitMQ**).
2.  **Processing:** A dedicated "Consumer Service" pulls messages from the queue in optimal batch sizes.
3.  **Storage:** These batches are flushed to ClickHouse efficiently.

**Benefits of this approach:**
* **Traffic Smoothing:** Sudden spikes in user logs won't crash the database; they simply fill up the queue.
* **Fault Tolerance:** If ClickHouse goes down for maintenance, logs are safely buffered in Kafka/RabbitMQ and processed once the DB is back online.
* **Decoupling:** Allows us to add real-time stream processing (e.g., alerting on errors) by simply adding another consumer to the queue.