*************
Webhooq Goals
=============
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



******
Status
======
Webhooq follows a 'release early, release often' development model. We are just finishing the PoC phase, there are and will be bugs. Today, we are not production-ready.
Development is focused on providing a complete implementation of the goals listed above first even if it affects performance initially.



****************
Building Webhooq
================
Assemble an executable jar.
```
mvn clean compile test assembly:single
```



***************
Running Webhooq
===============
Start webhooq on default port of 8080.

```
java -jar target/webhooq-1.0-SNAPSHOT-jar-with-dependencies.jar
```


Start webhooq on a different port (e.g 9090).
```
java -jar -Dnetty.port=9090 target/webhooq-1.0-SNAPSHOT-jar-with-dependencies.jar
```



*************
Using Webhook
=============
   *   Webhooq uses AMQP-inspired primitives: Exchanges, Queues and Routing Keys.
   *   Exchanges come in three types: direct, topic, and fanout.
   *   Exchanges and Queues are identified by a URL-safe name string.
   *   Queues are bound to an Exchange with a Routing Key.
   *   Messages are published to an Exchange with a Routing Key.


Exchanges
---------
Declare a topic exchange (e.g. my-exchange).
```
curl -v  -X POST http://localhost:8080/exchange/my-exchange?type=topic
```
Result:

| Code | Reason                                     |
|------|--------------------------------------------|
|  201 | Created                                    |
|  400 | Declaring an exchange that already exists. |


Delete an exchange (e.g. my-exchange).
```
curl -v  -X DELETE http://localhost:8080/exchange/my-exchange
```
Result:

| Code | Reason                                    |
|------|-------------------------------------------|
|  204 | No Content                                |
|  404 | Deleting an exchange that does not exist. |


Queues
------
Declare an exchange (e.g. my-queue).
```
curl -v  -X POST http://localhost:8080/queue/my-queue
```
Result:

| Code | Reason                                 |
|------|----------------------------------------|
|  201 | Created                                |
|  400 | Declaring a queue that already exists. |

Delete an exchange (e.g. my-exchange).
```
curl -v  -X DELETE http://localhost:8080/queue/my-queue
```
Result:

| Code | Reason                                |
|------|---------------------------------------|
|  204 | No Content                            |
|  404 | Deleting a queue that does not exist. |


Binding
-------
Exchanges can be bound to queues or other exchanges.

Bind an exchange (my-source) to another exchange (my-dest) using a routing key (a.*.*.d). Messages published to the source exchange that match the routing key will be delivered to the destination exchange.
```
curl -v -X POST -H 'x-wq-exchange:my-dest' -H 'x-wq-rkey:a.*.*.d' http://localhost:8080/exchange/my-source/bind
```
Result:

| Code | Reason                                                                                             |
|------|----------------------------------------------------------------------------------------------------|
|  201 | Created                                                                                            |
|  400 | If Routing Key, Source exchange, or Destination (exchange| (queue & link))  are missing/malformed. |


Bind an exchange (my-exchange) to a queue (my-queue) using a routing key (a.b.c.d) and the callback url (http://my-site.com). Messages published to the exchange that match the routing key will be delivered to the callback link.
```
curl -v -X POST -H 'x-wq-queue:my-dest' -H 'x-wq-rkey:a.b.c.d' -H 'x-wq-link:<http://my-site.com>; rel="wq"' http://localhost:8080/exchange/my-exchange/bind
```
Result:

| Code | Reason                                                                                             |
|------|----------------------------------------------------------------------------------------------------|
|  201 | Created                                                                                            |
|  400 | If Routing Key, Source exchange, or Destination (exchange| (queue & link))  are missing/malformed. |


Publishing
----------
Publishing is always done to an exchange.
Publishing is always asynchronous.

Publish a message (the contents of mess.txt) to an exchange (my-exchange) with a routing key (a.b.c.d).
```
cat mess.txt | curl -v  -X POST -H "Content-Type:text/plain"  -H "x-wq-rkey:a.b.c.d" --data-binary "@-" http://localhost:8080/exchange/my-exchange
```
Result:

| Code | Reason                                            |
|------|---------------------------------------------------|
|  202 | Accepted`                                         |
|  400 | If Routing Key or Exchange are missing/malformed. |





