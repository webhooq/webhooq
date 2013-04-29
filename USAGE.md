Using Webhook
-------------
   *   Webhooq uses AMQP-inspired primitives: Exchanges, Queues and Routing Keys.
   *   Exchanges come in three types: direct, topic, and fanout.
   *   Exchanges and Queues are identified by a URL-safe name string.
   *   Queues are bound to an Exchange with a Routing Key.
   *   Messages are published to an Exchange with a Routing Key.


## Exchanges


### Declare an exchange.

| Method | URL                                                               |
|--------|-------------------------------------------------------------------|
|  POST  | http://localhost:8080/exchange/:exchange-name?type=:exchange-type |

| Paramaters       | Description                                                             |
|------------------|-------------------------------------------------------------------------|
| `:exchange-name` | A URL-safe id of the exchange to create.                                |
| `:exchange-type` | The type of exchange to create, must be `direct`, `fanout`, or `topic`. |

| Headers          | Description             |
|------------------|-------------------------|
| `Host`           | Used to partition data. |

Result:

| Code | Reason                                     |
|------|--------------------------------------------|
|  201 | Created                                    |
|  400 | Declaring an exchange that already exists. |

A cURL example (e.g. Declare an exchange named `my-exchange` of type `topic`):
```
curl -v  -X POST http://localhost:8080/exchange/my-exchange?type=topic
```


### Delete an exchange

| Method | URL                                                               |
|--------|-----------------------------------------------|
| DELETE | http://localhost:8080/exchange/:exchange-name |

| Paramaters       | Description                              |
|------------------|------------------------------------------|
| `:exchange-name` | A URL-safe id of the exchange to delete. |

| Headers          | Description             |
|------------------|-------------------------|
| `Host`           | Used to partition data. |

Result:

| Code | Reason                                    |
|------|-------------------------------------------|
|  204 | No Content                                |
|  404 | Deleting an exchange that does not exist. |

A cURL example (e.g. Delete an exchange named `my-exchange`):
```
curl -v  -X DELETE http://localhost:8080/exchange/my-exchange
```


## Queues

### Declare an exchange (e.g. my-queue).
```
curl -v  -X POST http://localhost:8080/queue/my-queue
```
Result:

| Code | Reason                                 |
|------|----------------------------------------|
|  201 | Created                                |
|  400 | Declaring a queue that already exists. |

### Delete an exchange (e.g. my-exchange).
```
curl -v  -X DELETE http://localhost:8080/queue/my-queue
```
Result:

| Code | Reason                                |
|------|---------------------------------------|
|  204 | No Content                            |
|  404 | Deleting a queue that does not exist. |


## Binding

Exchanges can be bound to queues or other exchanges.

### Bind an exchange (my-source) to another exchange (my-dest) using a routing key (a.*.*.d). Messages published to the source exchange that match the routing key will be delivered to the destination exchange.
```
curl -v -X POST -H 'x-wq-exchange:my-dest' -H 'x-wq-rkey:a.*.*.d' http://localhost:8080/exchange/my-source/bind
```
Result:

| Code | Reason                                                                                             |
|------|----------------------------------------------------------------------------------------------------|
|  201 | Created                                                                                            |
|  400 | If Routing Key, Source exchange, or Destination (exchange| (queue & link))  are missing/malformed. |


### Bind an exchange (my-exchange) to a queue (my-queue) using a routing key (a.b.c.d) and the callback url (http://my-site.com). Messages published to the exchange that match the routing key will be delivered to the callback link.
```
curl -v -X POST -H 'x-wq-queue:my-dest' -H 'x-wq-rkey:a.b.c.d' -H 'x-wq-link:<http://my-site.com>; rel="wq"' http://localhost:8080/exchange/my-exchange/bind
```
Result:

| Code | Reason                                                                                             |
|------|----------------------------------------------------------------------------------------------------|
|  201 | Created                                                                                            |
|  400 | If Routing Key, Source exchange, or Destination (exchange| (queue & link))  are missing/malformed. |


## Publishing

Publishing is always done to an exchange.
Publishing is always asynchronous.

### Publish a message (the contents of mess.txt) to an exchange (my-exchange) with a routing key (a.b.c.d).
```
cat mess.txt | curl -v  -X POST -H "Content-Type:text/plain"  -H "x-wq-rkey:a.b.c.d" --data-binary "@-" http://localhost:8080/exchange/my-exchange
```
Result:

| Code | Reason                                            |
|------|---------------------------------------------------|
|  202 | Accepted`                                         |
|  400 | If Routing Key or Exchange are missing/malformed. |

