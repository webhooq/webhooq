Webhooq Goals
-------------
   *   Nodes are designed to be ephemeral, storing all data in the main memories of commodity servers.
   *   Messages are replicated over multiple nodes. Only a failure of all nodes simultaneously will result in data loss. Replication factor is tunable.
   *   Symmetrical system design, no master/slave configuration. All nodes are treated equally in the cluster.
   *   Consistency is governed by a dynamically elected coordinator via Hazelcast
   *   Additional throughput, redundancy, and capacity can be increased simply by adding additional webhooq instances to the cluster.
   *   No protocol-specific client. Simple HTTP interface. Message delivery is done via webhooks registered at queue bind time, rather than a dedicated connection as with AMQP. A benefit of this approach  is that webhooq will be more resilient to network volatility than a dedicated connection would be. A drawback of this approach is that webhooq is near-real-time(delivery within milliseconds) rather than real-time (as with a dedicated socket).
   *   AMQP inspired model, with exchanges (direct, topic, fanout), queues, and routing keys, including wildcards matching (*, #).
   *   Undelivered Messages live for up to 12 hours.
   *   Message size must be 64 kilobytes or less in size.
   *   Messages are delivered once. Message duplication is done within webhooq, eliminating the need for consumers to consult an auxiliary deduplication cache. Once webhooq receives an HTTP 2xx status code response from your webhook, a message is considered delivered. Webhooq will requeue any message that fails delivery.



Building Webhooq
----------------
Assemble an executable jar
`mvn clean compile test assembly:single`



Running Webhooq
---------------
Start webhooq on default port of 8080.
`java -jar target/webhooq-1.0-SNAPSHOT-jar-with-dependencies.jar`


Start webhooq on a different port (e.g 9090).
`java -jar -Dnetty.port=9090 target/webhooq-1.0-SNAPSHOT-jar-with-dependencies.jar`



Using Webhook
-------------
Webhooq uses the same primatives as AMQP: Exchanges, Queues and Routing Keys.

Exchanges come in three types: direct, topic, and fanout.

Exchanges and Queues are identified by a URL-safe name.

Queues are bound to an Exchange with a routing key.

Messages are published to an exchange with a routing key.


Exchanges
---------
Declare a topic exchange (e.g. my-exchange). Declaring an exchange that already exists will fail.
`curl -v  -X POST http://localhost:8080/exchange/my-exchange?type=topic`

Delete an exchange (e.g. my-exchange). Deleting an exchange that does not exist will fail.
`curl -v  -X DELETE http://localhost:8080/exchange/my-exchange`

Queues
------
Declare an exchange (e.g. my-queue).  Declaring a queue that already exists will fail.
`curl -v  -X POST http://localhost:8080/queue/my-queue`

Delete an exchange (e.g. my-exchange). Deleting a queue that does not exist will fail.
`curl -v  -X DELETE http://localhost:8080/queue/my-queue`

Binding
-------
Exchanges can be bound to queues or other exchanges.

Bind an exchange (my-source) to another exchange (my-dest) using a routing key (a.*.*.d). Messages published to the source exchange that match the routing key will be delivered to the destination exchange.
`curl -v -X POST -H 'x-wq-dest:my-dest' -H 'x-wq-rkey:a.*.*.d' http://localhost:8080/exchange/my-source/bind`

Bind an exchange (my-exchange) to a queue (my-queue) using a routing key (a.b.c.d) and the callback url (http://my-site.com). Messages published to the exchange that match the routing key will be delivered to the callback link.
`curl -v -X POST -H 'x-wq-queue:my-queue' -H 'x-wq-rkey:a.b.c.d' -H 'x-wq-link:<http://my-site.com>; rel="wq"' http://localhost:8080/exchange/my-exchange/bind`

Publishing
----------
Publishing is always done to an exchange.
Publishing is always asynchronous.

Publish a message (the contents of mess.txt) to an exchange (my-exchange) with a routing key (a.b.c.d)
`cat mess.txt | curl -v  -X POST -H "Content-Type:text/plain"  -H "x-wq-rkey:a.b.c.d" --data-binary "@-" http://localhost:8080/exchange/my-exchange`





