Webhooq Goals
-------------
   *   Nodes are designed to be ephemeral, storing all data in the main memories of commodity servers.
   *   Messages are replicated over multiple nodes. Only a failure of all nodes simultaneously will result in data loss. Replication factor is tunable.
   *   Symmetrical system design, no master/slave configuration. All nodes are treated equally in the cluster.
   *   Consistency is governed by a dynamically elected coordinator via Hazelcast
   *   Additional throughput, redundancy, and capacity can be increased simply by adding additional Webhooq instances to the cluster.
   *   No protocol-specific client. Simple HTTP interface. Message delivery is done via webhooks registered at queue bind time, rather than a dedicated connection as with AMQP. A benefit of this approach is that Webhooq will be more resilient to network volatility than a dedicated connection would be. A drawback of this approach is that Webhooq is near-real-time rather than real-time (as with a dedicated socket).
   *   AMQP inspired model, with exchanges (direct, topic, fanout), queues, and routing keys, including wildcards matching (*, #).
   *   Undelivered Messages live for up to 12 hours.
   *   Message size must be 64 kilobytes or less in size.
   *   Messages are delivered once. Message duplication is done within Webhooq, eliminating the need for consumers to consult an auxiliary deduplication cache. Once Webhooq receives an HTTP 2xx status code response from your webhook, a message is considered delivered. Webhooq will requeue any message that fails delivery.
   *   Informative error messages. If you encounter an error, it should describe exactly what was wrong with your request.



Status
------
Webhooq follows a 'release early, release often' development model. We are just finishing the PoC phase, there are and will be bugs. Today, we are not production-ready.
Development is focused on providing a complete implementation of the goals listed above first even if it affects performance initially.



Building Webhooq
----------------
##### Assemble an executable jar.
```
mvn clean compile test assembly:single
```


Running Webhooq
---------------

##### Start webhooq on default port of 8080.
```
java -jar target/webhooq-1.0-SNAPSHOT-jar-with-dependencies.jar
```


##### Start webhooq on a different port (e.g 9090).
```
java -jar -Dnetty.port=9090 target/webhooq-1.0-SNAPSHOT-jar-with-dependencies.jar
```


Using Webhook
-------------

API usage is documented in USAGE.md


