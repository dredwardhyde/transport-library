## Jaffa RPC library

**This library provides communication between Java Spring applications.**  

**Key features:**  
- **Apache ZooKeeper (with TLSv1.2) is used for service discovery**
- **Sync & async RPC calls - type of communication is determined by the client, not server**
- **One interface could have multiple server implementations - client choose required one by specifying target's jaffa.rpc.module.id in request**
- **Request-scoped timeout for both sync/async calls**
- **4 transport protocols are supported**:
  - **ZeroMQ (with authentication/encryption using Curve)**
    - Unlimited message size
    - Low latency
    - Pure TCP connection
  - **Apache Kafka (with TLSv1.2)**
    - Persistence (messages could be replayed)
    - High throughput
  - **HTTP1.1/HTTPS (with TLSv1.2)**
    - Low latency
    - High throughput
  - **RabbitMQ**
    - Low latency
    - High throughput
    - Persistence
- **2 serialization protocols are supported**:
  - **Java**
    - Slower
    - No compression
    - **All exceptions will be returned as is**
  - **Kryo**
    - Faster
    - More efficient
    - **Only stacktraces will be returned in response**
- **User could specify custom OTT provider (see example below)**

## Latency

Only **relative** latency could be estimated, because hardware and software varies greatly.   
**X axis** - sliding time window  
**Y axis** - response time in ms  
Dashboard URL is logged at startup like this:
```
2020-05-01 20:19:00 INFO  AdminServer:112 - Jaffa RPC console started at http://host.docker.internal:62842/admin
```
#### Synchronous RPC  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/http_sync.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/zmq_sync.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/kafka_sync.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/rabbit_sync.png" width="900"/>  

#### Synchronous RPC  (500kb request/ 500kb response)
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/http_heavy.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/zmq_heavy.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/rabbit_heavy.png" width="900"/>  

#### Asynchronous RPC  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/http_async.PNG" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/zmq_async.PNG" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/kafka_async.PNG" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/rabbit_async.png" width="900"/>  

## How to use

[FULL EXAMPLE HERE](https://github.com/dredwardhyde/jaffa-rpc-library/blob/master/src/test/java/com/jaffa/rpc/test/TestServer.java)

You create an interface with ```@Api```annotation like this:

```java
@Api
public interface PersonService {
    public static final String TEST = "TEST"; // will be ignored
    public static void lol3() { // will be ignored
        System.out.println("lol3");
    } // will be ignored
    public int add(String name, String email, Address address);
    public Person get(Integer id);
    public void lol();
    public void lol2(String message);
    public String getName();
    public Person testError();
}
```

**Server-side implementation (must be Spring Bean):**
```java

@ApiServer
@Component
public class PersonServiceImpl implements PersonService{
    // Methods
    // ...
    public void lol(){ // Normal invocation
        RequestContext.getSourceModuleId(); // client jaffa.rpc.module.id available on server side
        RequestContext.getTicket(); // and security ticket too (if it was provided by client)
    }
    public Person testError() { // Invocation thrown exception
        throw new RuntimeException("Exception in " + System.getProperty("jaffa.rpc.module.id"));
    }
}
```

Then [jaffa-rpc-maven-plugin](https://github.com/dredwardhyde/jaffa-rpc-maven-plugin) generates client interface.  
This plugin ignores all the static, default methods and all fields:

```java
@ApiClient(ticketProvider = TicketProviderImpl.class)
public interface PersonServiceClient {
    public Request<Integer> add(String name, String email, Address address);
    public Request<Person> get(Integer id);
    public Request<Void> lol();
    public Request<Void> lol2(String message);
    public Request<String> getName();
    public Request<Person> testError();
}
```

**OTT provider could be specified by user:**

```java
@Component
public class TicketProviderImpl implements TicketProvider {

    @Override
    public SecurityTicket getTicket() {
        // Specify user and security token
        return new SecurityTicket("user1", UUID.randomUUID().toString());
    }
}
```

**Next, you could inject this RPC interface using @Autowire and make sync or async RPC calls:**

```java
@Autowired
com.jaffa.rpc.test.PersonServiceClient personServiceClient;

// Sync call on any implementation with 10s timeout:
Integer id = personServiceClient.add("Test name", "test@mail.com", null)
                          .withTimeout(TimeUnit.MILLISECONDS.toMillis(15000))
                          .onModule("test.server")
                          .executeSync();

// Async call on module with moduleId = main.server and timeout = 10s
personServiceClient.get(id)
             .onModule("main.server")
             .withTimeout(TimeUnit.MILLISECONDS.toMillis(10000))
             .executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
```

**Async callback implementation (must be Spring Bean):**  
```java
@Component
public class PersonCallback implements Callback<Person> {

    // **key** - used as request ID, will be the same value that was used during invocation
    // **result** - result of method invocation
    // This method will be called if method was executed without exceptions
    // If T is Void then result will always be **null**
    @Override
    public void onSuccess(String key, Person result) {
        System.out.println("Key: " + key);
        System.out.println("Result: " + result);
    }

    // This method will be called if method has thrown exception OR execution timeout occurred
    @Override
    public void onError(String key, Throwable exception) {
        System.out.println("Exception during async call");
        exception.printStackTrace();
    }
}

```

## Exceptions  
<table>
  <tr>
    <td>JaffaRpcExecutionException</td>
    <td>If any exception occurred during sending request or receiving response</td>
  </tr>
  <tr>
    <td>JaffaRpcSystemException</td>
    <td>If any system resource not available (ZooKeeper/Kafka/RabbitMQ/OS)</td>
  </tr>
  <tr>
    <td>JaffaRpcNoRouteException</td>
    <td>If request could not be send (required jaffa.rpc.module.id is not available now)</td>
  </tr>
  <tr>
    <td>JaffaRpcExecutionTimeoutException</td>
    <td>If response was not received until timeout (specified by client or 1 hour as default)</td>
  </tr>
</table>  

## Configuration

```java
@Configuration
@ComponentScan
@Import(JaffaRpcConfig.class) // Import Jaffa RPC library configuration
public class MainConfig {

    // Specify server implementation endpoints (must be empty if none exists)
    @Bean
    ServerEndpoints serverEndpoints(){ 
        return new ServerEndpoints(PersonServiceImpl.class, ClientServiceImpl.class); 
    }

    // Specify required client endpoints (must be empty if none exists)
    @Bean
    ClientEndpoints clientEndpoints(){ 
        return new ClientEndpoints(ClientServiceClient.class, PersonServiceClient.class); 
    }
}
```

NOTE: Number of partitions for library's topics is equal to the number of Kafka brokers.
      If any required topics already exist, but they have wrong configurations, exception will be thrown.

#### Available options
Could be configured entirely as JVM options or by specifying **jaffa-rpc-config** JVM option with the path to [config.properties](https://github.com/dredwardhyde/jaffa-rpc-library/blob/master/jaffa-rpc-config-main-server.properties)
<table>
  <th>Option</th><th>Description</th>
  <tr>
    <td>jaffa.rpc.zookeeper.connection</td>
    <td>ZooKeeper cluster connection string (required): 'host:port' </td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.zmq.service.port</td>
    <td>Port for receiving request connections for ZeroMQ (optional, default port is 4242)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.http.service.port</td>
    <td>Port for receiving request connections for HTTP (optional, default port is 4242)</td> 
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.zmq.callback.port</td>
    <td>Port for receiving callback connections for ZeroMQ (optional, default port is 4342)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.http.callback.port</td>
    <td>Port for receiving callback connections for HTTP (optional, default port is 4342)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.module.id</td>
    <td>Unique name of server in ZooKeeper cluster (required)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol</td>
    <td>Could be 'zmq', 'kafka', 'http', 'rabbit' (required)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.kafka.bootstrap.servers</td>
    <td>Bootstrap servers of Kafka cluster  (optional, only when RPC protocol is Kafka)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.rabbit.host</td>
    <td>RabbitMQ server host (optional, only when RPC protocol is RabbitMQ)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.rabbit.port</td>
    <td>RabbitMQ server port (optional, only when RPC protocol is RabbitMQ)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.serializer</td>
    <td>Serialization providers available: 'kryo' (default) and 'java'. Java serialization requires all entities to be Serializable. Same serialization provider must be used clusterwide.</td>
  </tr>
  <tr>
    <td>jaffa.admin.keystore</td>
    <td>Path to PKCS12 keystore that will be used to configure HTTPS server for admin console</td>
  </tr>
  <tr>
    <td>jaffa.admin.storepass</td>
    <td>Password to keystore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.admin.use.https</td>
    <td>Use HTTPS or HTTP for admin console, HTTP is default</td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.use.https</td>
    <td>Enables HTTPS when 'http' protocol is used. 'false' by default</td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.https.keystore</td>
    <td>Path to PKCS12 keystore that will be used to configure HTTPS server for RPC communication</td>
  </tr>
  <tr>
    <td>jaffa.rpc.protocol.https.storepass</td>
    <td>Password to keystore provided by previous option</td>
  </tr>
    <tr>
    <td>jaffa.rpc.zookeeper.client.secure</td>
    <td>Value 'true' enables TLSv1.2 for Apache ZooKeeper client</td>
  </tr>
  <tr>
    <td>jaffa.rpc.zookeeper.clientCnxnSocket</td>
    <td>Must be 'org.apache.zookeeper.ClientCnxnSocketNetty' if TLSv1.2 is enabled</td>
  </tr>
  <tr>
    <td>jaffa.rpc.zookeeper.ssl.keyStore.location</td>
    <td>Path to JKS keystore that will be used to connect to Apache ZooKeeper</td>
  </tr>
  <tr>
    <td>jaffa.rpc.zookeeper.ssl.keyStore.password</td>
    <td>Password to keystore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.rpc.zookeeper.ssl.trustStore.location</td>
    <td>Path to JKS truststore that will be used to connect to Apache ZooKeeper</td>
  </tr>
  <tr>
    <td>jaffa.rpc.zookeeper.ssl.trustStore.password</td>
    <td>Password to truststore provided by previous option</td>
  </tr>
    <tr>
      <td>jaffa.rpc.protocol.kafka.use.ssl</td>
      <td>Value 'true' enables TLSv1.2 for Apache Kafka</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.kafka.ssl.truststore.location</td>
      <td>Path to JKS truststore that will be used to connect to Apache Kafka</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.kafka.ssl.truststore.password</td>
      <td>Password to truststore provided by previous option</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.kafka.ssl.keystore.location</td>
      <td>Path to JKS keystore that will be used to connect to Apache Kafka</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.kafka.ssl.keystore.password</td>
      <td>Password to keystore provided by previous option</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.kafka.ssl.key.password</td>
      <td>Password to key in keystore by previous options</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.zmq.curve.enabled</td>
      <td>Enables Curve security for ZeroMQ protocol</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.zmq.client.dir</td>
      <td>Directory with Curve certificates for client requests</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.zmq.server.keys</td>
      <td>Path to the Curve keys for current server</td>
    </tr>
    <tr>
      <td>jaffa.rpc.protocol.zmq.client.key.--jaffa.rpc.module.id--</td>
      <td>Path to the Curve keys for client with --jaffa.rpc.module.id--</td>
    </tr>
  </table>  
  
## Work in progress:  

### gRPC support   
### Login&Password/TLS 1.2 support for RabbitMQ  

## Example how to generate keystore for admin console:  
```sh
keytool -genkeypair -keyalg RSA -alias self_signed -keypass simulator -keystore test.keystore -storepass simulator
```

## Example how to generate self-signed truststore and keystore for development purposes:  
Please note that Common Name must be equal to $hostname  
```sh
keytool -genkey -alias bmc -keyalg RSA -keystore keystore.jks -keysize 2048
openssl req -new -x509 -keyout ca-key -out ca-cert
keytool -keystore keystore.jks -alias bmc -certreq -file cert-file
openssl x509 -req -CA ca-cert -CAkey ca-key -in cert-file -out cert-signed -days 365 -CAcreateserial -passin pass:simulator
keytool -keystore keystore.jks -alias CARoot -import -file ca-cert
keytool -keystore keystore.jks -alias bmc -import -file cert-signed
keytool -keystore truststore.jks -alias bmc -import -file ca-cert
```
