# Using Webhook
 * Webhooq uses AMQP-inspired primitives: Exchanges, Queues and Routing Keys.
 * Exchanges come in three types: `direct`, `topic`, and `fanout`.
 * Exchanges and Queues are identified by a URL-safe name string.
 * Queues are bound to an Exchange with a Routing Key.
 * Messages are published to an Exchange with a Routing Key.
 * Messages a just HTTP requests to the Exchange. All headers and the request body are forwarded to the Exchange's bindings, based on the Exchange's type and the Routing Key used in the binding.
 * [Exchanges](#exchanges)
    * [Declare an Exchange](#declare-an-exchange)
    * [Delete an Exchange](#delete-an-exchange)
 * [Queues](#queues)
    * [Declare an Queue](#declare-an-queue)
    * [Delete an Queue](#delete-an-queue)
 * [Binding](#binding)
    * [Declare a Binding](#declare-a-binding)
    * [Delete a Binding](#delete-a-binding)
 * [Publishing](#publishing)

--

# Exchanges

### Declare an Exchange.

#### Request:

| Method | URL                                                                   |
|--------|-----------------------------------------------------------------------|
|  POST  | http://localhost:8080/exchange/`:exchange-name`?type=`:exchange-type` |

| Parameters       | Description                                                             |
|------------------|-------------------------------------------------------------------------|
| `:exchange-name` | A URL-safe id of the exchange to create.                                |
| `:exchange-type` | The type of exchange to create, must be `direct`, `fanout`, or `topic`. |

| Headers | Description             |
|---------|-------------------------|
| `Host`  | Used to partition data. |

#### Response:

|  Code  | Reason                                     |
|--------|--------------------------------------------|
|  `201` | Created                                    |
|  `400` | Declaring an exchange that already exists. |

#### Example:

Use cURL to declare an Exchange named `my-exchange` of type `topic`:
```
curl -v  -X POST http://localhost:8080/exchange/my-exchange?type=topic
```

### Delete an Exchange.

#### Request:

| Method | URL                                             |
|--------|-------------------------------------------------|
| DELETE | http://localhost:8080/exchange/`:exchange-name` |

| Parameters       | Description                              |
|------------------|------------------------------------------|
| `:exchange-name` | A URL-safe id of the exchange to delete. |

| Headers | Description             |
|---------|-------------------------|
| `Host`  | Used to partition data. |

#### Response:

|  Code  | Reason                                    |
|--------|-------------------------------------------|
|  `204` | No Content                                |
|  `404` | Deleting an exchange that does not exist. |

#### Example:

Use cURL to delete an Exchange named `my-exchange`:
```
curl -v  -X DELETE http://localhost:8080/exchange/my-exchange
```

--

# Queues

### Declare a Queue.

#### Request:

| Method | URL                                       |
|--------|-------------------------------------------|
|  POST  | http://localhost:8080/queue/`:queue-name` |

| Parameters    | Description                           |
|---------------|---------------------------------------|
| `:queue-name` | A URL-safe id of the queue to create. |

| Headers | Description             |
|---------|-------------------------|
| `Host`  | Used to partition data. |

#### Response:

| Code  | Reason                                 |
|-------|----------------------------------------|
| `201` | Created                                |
| `400` | Declaring a queue that already exists. |

#### Example:

Use cURL to declare a Queue named `my-queue`:
```
curl -v  -X POST http://localhost:8080/queue/my-queue
```


### Delete a Queue.

#### Request:

| Method | URL                                       |
|--------|-------------------------------------------|
| DELETE | http://localhost:8080/queue/`:queue-name` |

| Parameters    | Description                           |
|---------------|---------------------------------------|
| `:queue-name` | A URL-safe id of the queue to delete. |

| Headers | Description             |
|---------|-------------------------|
| `Host`  | Used to partition data. |

#### Response:

| Code  | Reason                                |
|-------|---------------------------------------|
| `204` | No Content                            |
| `404` | Deleting a queue that does not exist. |

#### Example:

Use cURL to delete a Queue named `my-queue` :
```
curl -v  -X DELETE http://localhost:8080/queue/my-queue
```

--

# Binding

Exchanges can be bound to queues or other exchanges.

### Declare a Binding.

#### Request:

| Method | URL                                                  |
|--------|------------------------------------------------------|
|  POST  | http://localhost:8080/exchange/`:exchange-name`/bind |

| Parameters       | Description                                                                 |
|------------------|-----------------------------------------------------------------------------|
| `:exchange-name` | A URL-safe id of the exchange to use as the source exchange in the binding. |

| Headers         | Description             |
|-----------------|-------------------------|
| `Host`          | Used to partition data. |
| `x-wq-rkey`     | The Routing Key to use for this binding. Messages published with a matching Routing Key (based on exchange type) will be delivered to this binding's destination. |
|    EITHER       |                         |
| `x-wq-exchange` | Defines an exchange as the destination to bind the source exchange to. Messages published to the source exchange with a matching Routing Key (based on the source Exchange type) will be delivered to this exchange. |
|      OR         |                         |
| `x-wq-queue`    | Defines a queue as the destination to bind the source exchange to. Messages published to the source exchange with a matching Routing Key (based on the source Exchange's type) will be delivered to this queue's link (defined by `x-wq-link`). |
| `x-wq-link`     | An rfc5988 web link used to define the webhook to invoke when a message is delivered to the queue defined in `x-wq-queue`. The uri defined with a link-param rel="wq" will be invoked delivering messages. |

#### Response:

| Code  | Reason                                                                                                      |
|-------|-------------------------------------------------------------------------------------------------------------|
| `201` | Created                                                                                                     |
| `400` | If Routing Key, Source exchange, or Destination (exchange or (queue + link)) headers are missing/malformed. |

#### Example:

Use cURL to bind an exchange named `my-source` to another exchange named `my-dest` using a Routing Key of `a.*.*.d`.
Messages published to the source exchange that match the Routing Key will be delivered to the destination exchange.
```
curl -v -X POST -H 'x-wq-exchange:my-dest' -H 'x-wq-rkey:a.*.*.d' http://localhost:8080/exchange/my-source/bind
```

Use cURL to bind an exchange named `my-exchange` to a queue named `my-queue` using a Routing Key of `a.b.c.d` and the callback url of `http://callback.yoursite.com/`.
Messages published to `my-exchange` that match the Routing Key  of `a.b.c.d` will be delivered to the callback url.
```
curl -v -X POST -H 'x-wq-queue:my-dest' -H 'x-wq-rkey:a.b.c.d' -H 'x-wq-link:<http://callback.yoursite.com>; rel="wq"' http://localhost:8080/exchange/my-exchange/bind
```

### Delete a Binding.

Not implemented yet.

--

# Publishing

 * Publishing is always done to an exchange.
 * Publishing is always asynchronous.
 * A Message is the contents of the HTTP request. All headers and the body of the publish request will be sent to the Exchange's bindings.

#### Request:

| Method | URL                                                     |
|--------|---------------------------------------------------------|
|  POST  | http://localhost:8080/exchange/`:exchange-name`/publish |

| Parameters       | Description                                                                 |
|------------------|-----------------------------------------------------------------------------|
| `:exchange-name` | A URL-safe id of the exchange to publish the message to. |

| Headers         | Description             |
|-----------------|-------------------------|
| `Host`          | Used to partition data. |
| `x-wq-rkey`     | The Routing Key to use for this publish. Any bindings this exchange has with a matching Routing Key (based on exchange type) will receive this message. |

#### Response:

| Code  | Reason                                            |
|-------|---------------------------------------------------|
| `202` | Accepted                                          |
| `400` | If Routing Key or Exchange are missing/malformed. |

#### Example

Use cURL to publish a text Message from the contents of a file named mess.txt, to an Exchange named `my-exchange` with a Routing Key of `a.b.c.d`.
```
cat mess.txt | curl -v  -X POST -H "Content-Type:text/plain"  -H "x-wq-rkey:a.b.c.d" --data-binary "@-" http://localhost:8080/exchange/my-exchange
```