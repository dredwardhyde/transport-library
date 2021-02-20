[![Codacy Badge](https://api.codacy.com/project/badge/Grade/04f6718617474f7e937885607646fa10)](https://app.codacy.com/manual/dredwardhyde/jaffa-rpc-library?utm_source=github.com&utm_medium=referral&utm_content=dredwardhyde/jaffa-rpc-library&utm_campaign=Badge_Grade_Dashboard)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.dredwardhyde/jaffa-rpc-library/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.dredwardhyde/jaffa-rpc-library)
[![javadoc](https://javadoc.io/badge2/com.github.dredwardhyde/jaffa-rpc-library/javadoc.svg)](https://javadoc.io/doc/com.github.dredwardhyde/jaffa-rpc-library)
[![Build Status](https://github.com/dredwardhyde/jaffa-rpc-library/workflows/build/badge.svg)](https://github.com/dredwardhyde/jaffa-rpc-library/actions)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fdredwardhyde%2Fjaffa-rpc-library.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fdredwardhyde%2Fjaffa-rpc-library?ref=badge_shield)
[![codecov](https://codecov.io/gh/dredwardhyde/jaffa-rpc-library/branch/master/graph/badge.svg)](https://codecov.io/gh/dredwardhyde/jaffa-rpc-library)
## Jaffa RPC library

**This library provides RPC communication between Java Spring applications.**  

**Key features:**  
- **Apache ZooKeeper (with TLSv1.2) is used for service discovery**
- **Synchronous & asynchronous RPC calls - type of communication is determined by the client, not server**
- **One interface could have multiple server implementations - client choose required one by specifying target's jaffa.rpc.module.id in request**
- **Request-scoped timeout for both sync/async calls**
- **[Caffeine](https://github.com/ben-manes/caffeine) is used for caching service discovery information**
- **5 transport protocols are supported**:
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
  - **gRPC (with TLSv1.2)**
    - Very high throughput
  - **RabbitMQ (Login/Password with TLS 1.2)**
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
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/http_sync.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/zmq_sync.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/kafka_sync.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/rabbit_sync.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/grpc_sync.png" width="900"/>  

#### Synchronous RPC  (500kb request/ 500kb response)
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/http_heavy.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/zmq_heavy.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/kafka_heavy.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/rabbit_heavy.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/grpc_heavy.png" width="900"/> 

#### Asynchronous RPC  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/http_async.PNG" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/zmq_async.PNG" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/kafka_async.PNG" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/rabbit_async.png" width="900"/>  
<img src="https://raw.githubusercontent.com/dredwardhyde/jaffa-rpc-library/master/bench/grpc_async.png" width="900"/>

## How to use

[Full code example here](https://github.com/dredwardhyde/jaffa-rpc-library/blob/master/src/test/java/com/jaffa/rpc/test/TestServer.java)  
[Full project example here](https://github.com/dredwardhyde/jaffa-rpc-library-test)  
You create an interface with ```@Api```annotation like this:  

```java
@Api
public interface PersonService {
    String TEST = "TEST"; // Will be ignored by maven plugin
    static void lol3() { // Will be ignored by maven plugin
        System.out.println("lol3");
    }
    int add(String name, String email, Address address);
    Person get(Integer id);
    void lol();
    void lol2(String message);
    String getName();
    Person testError();
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
        RequestContextHelper.getSourceModuleId(); // Client's 'jaffa.rpc.module.id' is available on server side
        RequestContextHelper.getTicket(); // Security ticket is available too (if it was provided by client)
    }
    public Person testError() { // Invocation with exception
        throw new RuntimeException("Exception in " + System.getProperty("jaffa.rpc.module.id"));
    }
}
```

Then [jaffa-rpc-maven-plugin](https://github.com/dredwardhyde/jaffa-rpc-maven-plugin) generates client interface.  
This plugin ignores all the static, default methods and all fields:

```java
@ApiClient
public interface PersonServiceClient {
    Request<Integer> add(String name, String email, Address address);
    Request<Person> get(Integer id);
    Request<Void> lol();
    Request<Void> lol2(String message);
    Request<String> getName();
    Request<Person> testError();
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

// Sync call with 10s timeout:
Integer id = personServiceClient.add("Test name", "test@mail.com", null)
                          .withTimeout(15, TimeUnit.SECONDS)
                          .onModule("test.server")
                          .executeSync();

// Async call on module with module id = 'main.server' and timeout = 10s
personServiceClient.get(id)
             .onModule("main.server")
             .withTimeout(10, TimeUnit.SECONDS)
             .executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
```

**Async callback implementation (must be Spring Bean):**  
```java
@Slf4j
@Component
public class PersonCallback implements Callback<Person> {

    // **key** - used as request id, provided by client
    // **result** - result of method invocation
    // This method will be called if method was executed without exceptions
    // If T is Void then result will always be **null**
    @Override
    public void onSuccess(String key, Person result) {
        log.info("Key: {}", key);
        log.info("Result: {}", result);
    }

    // This method will be called if method has thrown exception OR execution timeout occurred
    @Override
    public void onError(String key, Throwable exception) {
        log.error("Exception during async call:", exception);
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

    // Client endpoint with ticket provider
    @Bean
    public ClientEndpoint clientEndpoint() {
        return new ClientEndpoint(ClientServiceClient.class, TicketProviderImpl.class);
    }
    
    // Client endpoint without ticket provider
    @Bean
    public ClientEndpoint personEndpoint() {
        return new ClientEndpoint(PersonServiceClient.class);
    }

    // Cluster-wide unique application/module identifier
    @Bean
    public String moduleId(){
        return "main.server";
    }
}
```

NOTE: Number of partitions for library's topics is equal to the number of Kafka brokers.
      If any required topics already exist, but they have wrong configurations, exception will be thrown.

#### Available options
Could be configured as JVM options or by specifying **jaffa-rpc-config** JVM option with the path to [config.properties](https://github.com/dredwardhyde/jaffa-rpc-library/blob/master/jaffa-rpc-config-main-server.properties)  

#### Required options (minimal config is available [here](https://github.com/dredwardhyde/jaffa-rpc-library-test/blob/master/config.properties)):  
<table width="900">
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol</td>
    <td>Could be <b>zmq</b>, <b>kafka</b>, <b>http</b>, <b>rabbit</b>, <b>grpc</b></td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.zookeeper.connection</td>
    <td>ZooKeeper cluster connection string: <b>host:port</b> </td>
  </tr>
  </table>  

#### Optional:  

  <table>
  <th>Option</th><th>Description</th>
    <tr>
    <td>jaffa.rpc.<b>module.id</b>.serializer</td>
    <td>Serialization providers available: 'kryo' (default) and 'java'. Java serialization requires all entities to be Serializable. Same serialization provider must be used clusterwide.</td>
  </tr>
    <tr>
    <td>jaffa.rpc.<b>module.id</b>.zookeeper.client.secure</td>
    <td>Value 'true' enables TLSv1.2 for Apache ZooKeeper client</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.zookeeper.clientCnxnSocket</td>
    <td>Must be 'org.apache.zookeeper.ClientCnxnSocketNetty' if TLSv1.2 is enabled</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.zookeeper.ssl.keyStore.location</td>
    <td>Path to JKS keystore that will be used to connect to Apache ZooKeeper</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.zookeeper.ssl.keyStore.password</td>
    <td>Password to keystore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.zookeeper.ssl.trustStore.location</td>
    <td>Path to JKS truststore that will be used to connect to Apache ZooKeeper</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.zookeeper.ssl.trustStore.password</td>
    <td>Password to truststore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.<b>module.id</b>.admin.use.https</td>
    <td>Use HTTPS or HTTP for admin console, HTTP is default</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.admin.ssl.keystore.location</td>
    <td>Path to JKS keystore that will be used to configure HTTPS server for admin console</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.admin.ssl.keystore.password</td>
    <td>Password to keystore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.admin.ssl.truststore.location</td>
    <td>Path to JKS truststore that will be used to configure HTTPS server for admin console</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.admin.ssl.truststore.password</td>
    <td>Password to truststore provided by previous option</td>
  </tr>
   <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.service.port</td>
    <td>Port for receiving request connections for HTTP (optional, default port is 4242)</td> 
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.callback.port</td>
    <td>Port for receiving callback connections for HTTP (optional, default port is 4342)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.use.https</td>
    <td>Enables HTTPS when 'http' protocol is used. 'false' by default</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.server.keystore.location</td>
    <td>Path to JKS keystore that will be used to configure HTTPS server for RPC communication</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.server.keystore.password</td>
    <td>Password to keystore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.server.truststore.location</td>
    <td>Path to JKS truststore that will be used to configure HTTPS server for RPC communication</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.server.truststore.password</td>
    <td>Password to truststore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.client.keystore.location</td>
    <td>Path to JKS keystore that will be used to configure HTTPS client for RPC communication</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.client.keystore.password</td>
    <td>Password to keystore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.client.truststore.location</td>
    <td>Path to JKS truststore that will be used to configure HTTPS client for RPC communication</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.http.ssl.client.truststore.password</td>
    <td>Password to truststore provided by previous option</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.kafka.bootstrap.servers</td>
    <td>Bootstrap servers of Kafka cluster  (optional, only when RPC protocol is Kafka)</td>
  </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.kafka.use.ssl</td>
      <td>Value 'true' enables TLSv1.2 for Apache Kafka</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.kafka.ssl.truststore.location</td>
      <td>Path to JKS truststore that will be used to connect to Apache Kafka</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.kafka.ssl.truststore.password</td>
      <td>Password to truststore provided by previous option</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.kafka.ssl.keystore.location</td>
      <td>Path to JKS keystore that will be used to connect to Apache Kafka</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.kafka.ssl.keystore.password</td>
      <td>Password to keystore provided by previous option</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.kafka.ssl.key.password</td>
      <td>Password to key in keystore by previous options</td>
    </tr>
      <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.zmq.service.port</td>
    <td>Port for receiving request connections for ZeroMQ (optional, default port is 4242)</td>
  </tr>
    <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.zmq.callback.port</td>
    <td>Port for receiving callback connections for ZeroMQ (optional, default port is 4342)</td>
  </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.zmq.curve.enabled</td>
      <td>Enables Curve security for ZeroMQ protocol</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.zmq.client.dir</td>
      <td>Directory with Curve certificates for client requests</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.zmq.server.keys</td>
      <td>Path to the Curve keys for current server</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.zmq.client.key.<b>module.id</b></td>
      <td>Path to the Curve keys for client with given <b>module.id</b></td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.login</td>
      <td>Login to RabbitMQ server ('guest' is default)</td>
    </tr>
      <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.host</td>
    <td>RabbitMQ server host (optional, only when RPC protocol is RabbitMQ)</td>
  </tr>
  <tr>
    <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.port</td>
    <td>RabbitMQ server port (optional, only when RPC protocol is RabbitMQ)</td>
  </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.password</td>
      <td>Password to RabbitMQ server ('guest' is default)</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.use.ssl</td>
      <td>Enables TLSv1.2 for connections to RabbitMQ ('false' is default)</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.ssl.keystore.location</td>
      <td>Path to JKS keystore that will be used to connect to RabbitMQ</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.ssl.keystore.password</td>
      <td>Password to keystore provided by previous option</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.ssl.truststore.location</td>
      <td>Path to JKS truststore that will be used to connect to RabbitMQ</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.rabbit.ssl.truststore.password</td>
      <td>Password to truststore provided by previous option</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.grpc.use.ssl</td>
      <td>Enables TLSv1.2 for connections to gRPC ('false' is default)</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.grpc.ssl.server.key.location</td>
      <td>Path to server PKCS private key file in PEM format</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.grpc.ssl.server.store.location</td>
      <td>Path to server X.509 certificate chain file in PEM format</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.grpc.ssl.client.key.location</td>
      <td>Path to client PKCS private key file in PEM format</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.grpc.ssl.client.keystore.location</td>
      <td>Path to client X.509 certificate chain file in PEM format</td>
    </tr>
    <tr>
      <td>jaffa.rpc.<b>module.id</b>.protocol.grpc.ssl.client.truststore.location</td>
      <td>Path to trusted certificates for verifying the remote endpoint's certificate. 
        The file should contain an X.509 certificate collection in PEM format.</td>
    </tr>
  </table>  
  
## How to generate self-signed keystore for admin console:  
```sh
keytool -genkeypair -keyalg RSA -alias self_signed -keypass simulator -keystore test.keystore -storepass simulator
```

## How to generate self-signed truststore and keystore (for development purposes):  
```sh
keytool -genkey -alias bmc -keyalg RSA -keystore keystore.jks -keysize 2048 -dname "CN=192.168.1.151,OU=Test,O=Test,C=RU" -ext "SAN:c=DNS:localhost,IP:127.0.0.1,IP:192.168.1.151" -storepass simulator -keypass simulator -deststoretype pkcs12
openssl req -new -nodes -x509 -keyout ca-key -out ca-cert -subj "/C=CN/ST=GD/L=SZ/O=Acme, Inc./CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:192.168.1.151"
keytool -keystore keystore.jks -alias bmc -certreq -file cert-file -storepass simulator -keypass simulator
openssl x509 -req -CA ca-cert -CAkey ca-key -in cert-file -out cert-signed -days 365 -CAcreateserial -passin pass:simulator
keytool -keystore keystore.jks -alias CARoot -import -file ca-cert -storepass simulator -noprompt
keytool -keystore keystore.jks -alias bmc -import -file cert-signed -storepass simulator -noprompt
keytool -keystore truststore.jks -alias bmc -import -file ca-cert -storepass simulator -noprompt
```

## How to generate self-signed keys and stores for gRPC protocol (for development purposes):
```sh
openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes -keyout server.key -out server.crt -subj "/C=CN/ST=GD/L=SZ/O=Acme, Inc./CN=192.168.1.151" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:192.168.1.151"

openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes -keyout client.key -out client.crt -subj "/C=CN/ST=GD/L=SZ/O=Acme, Inc./CN=192.168.1.151" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:192.168.1.151"
```

## Usage with JDK11+

Because of bug [JDK-8208526](https://bugs.openjdk.java.net/browse/JDK-8208526) you need to use this JVM option
```sh
-Djdk.tls.acknowledgeCloseNotify=true
```


## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fdredwardhyde%2Fjaffa-rpc-library.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fdredwardhyde%2Fjaffa-rpc-library?ref=badge_large)
