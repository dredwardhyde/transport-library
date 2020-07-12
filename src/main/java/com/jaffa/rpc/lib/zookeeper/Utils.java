package com.jaffa.rpc.lib.zookeeper;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.jaffa.rpc.lib.common.Options;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.grpc.GrpcRequestSender;
import com.jaffa.rpc.lib.http.HttpRequestSender;
import com.jaffa.rpc.lib.kafka.KafkaRequestSender;
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Utils {

    @Getter
    private static final List<String> services = new ArrayList<>();
    private static final Map<Protocol, Class<? extends Sender>> senders = new HashMap<>();
    @Getter
    @Setter
    private static volatile ZooKeeperConnection conn;
    private static ZooKeeper zk;
    public static final LoadingCache<String, byte[]> cache = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(10, TimeUnit.MINUTES).build(k -> zk.getData(k, true, null));

    static {
        senders.put(Protocol.ZMQ, ZeroMqRequestSender.class);
        senders.put(Protocol.HTTP, HttpRequestSender.class);
        senders.put(Protocol.KAFKA, KafkaRequestSender.class);
        senders.put(Protocol.RABBIT, RabbitMQRequestSender.class);
        senders.put(Protocol.GRPC, GrpcRequestSender.class);
    }

    public static void loadExternalProperties() {
        try {
            String path = System.getProperty("jaffa-rpc-config");
            if (Objects.nonNull(path)) {
                log.info("Loading Jaffa RPC properies from file {}", path);
                Properties p = new Properties();
                InputStream is = new FileInputStream(path);
                p.load(is);
                for (String name : p.stringPropertyNames()) {
                    String value = p.getProperty(name);
                    if (name.toLowerCase().contains("password"))
                        log.info("Loading property {} = {}", name, "*************");
                    else
                        log.info("Loading property {} = {}", name, value);
                    System.setProperty(name, value);
                }
            }
        } catch (IOException ioException) {
            log.error("Unable to read properties from jaffa-rpc-config file", ioException);
        }
    }

    public static void connect(String url) {
        try {
            conn = new ZooKeeperConnection();
            zk = conn.connect(url);
            ShutdownHook shutdownHook = new ShutdownHook();
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IOException | InterruptedException e) {
            log.error("Can not connect to ZooKeeper cluster", e);
            throw new JaffaRpcSystemException(e);
        }
    }

    public static String getRequiredOption(String option) {
        String optionValue = System.getProperty(option);
        if (StringUtils.isBlank(optionValue))
            throw new IllegalArgumentException("Property " + option + "  was not set");
        else return optionValue.trim();
    }

    public static String getServiceInterfaceNameFromClient(String clientName) {
        return clientName.replaceAll("Client$", "");
    }

    public static Pair<String, String> getHostForService(String service, String moduleId, Protocol protocol) {
        service = Utils.getServiceInterfaceNameFromClient(service);
        try {
            MutablePair<String, String> host = getHostsForService("/" + service, moduleId, protocol).get(0);
            if (protocol.equals(Protocol.HTTP)) {
                host.left = getHttpPrefix() + host.left;
            }
            return host;
        } catch (ParseException e) {
            throw new JaffaRpcNoRouteException(service);
        }
    }

    private static String getHttpPrefix() {
        return (Boolean.parseBoolean(System.getProperty(Options.USE_HTTPS, String.valueOf(false))) ? "https" : "http") + "://";
    }

    private static ArrayList<MutablePair<String, String>> getHostsForService(String service, String moduleId, Protocol protocol) throws ParseException {
        byte[] zkData = cache.get(service);
        if (Objects.isNull(zkData))
            throw new JaffaRpcNoRouteException(service);
        JSONArray jArray = (JSONArray) new JSONParser().parse(new String(zkData));
        if (jArray.isEmpty())
            throw new JaffaRpcNoRouteException(service);
        else {
            ArrayList<MutablePair<String, String>> hosts = new ArrayList<>();
            for (Object json : jArray) {
                String[] params = ((String) json).split("#");
                if (Objects.nonNull(moduleId)) {
                    if (moduleId.equals(params[1]) && protocol.getShortName().equals(params[2]))
                        hosts.add(new MutablePair<>(params[0], params[1]));
                } else {
                    if (protocol.getShortName().equals(params[2])) hosts.add(new MutablePair<>(params[0], params[1]));
                }
            }
            if (hosts.isEmpty())
                throw new JaffaRpcNoRouteException(service, moduleId);
            return hosts;
        }
    }

    public static String getModuleForService(String service, Protocol protocol) {
        try {
            byte[] zkData = cache.get("/" + service);
            JSONArray jArray = (JSONArray) new JSONParser().parse(new String(zkData));
            if (jArray.isEmpty())
                throw new JaffaRpcNoRouteException(service);
            else {
                ArrayList<String> hosts = new ArrayList<>();
                for (Object json : jArray) {
                    String[] params = ((String) json).split("#");
                    if (protocol.getShortName().equals(params[2])) hosts.add(params[1]);
                }
                if (hosts.isEmpty())
                    throw new JaffaRpcNoRouteException(service, protocol);
                return hosts.get(0);
            }
        } catch (ParseException e) {
            log.error("Error while getting avaiable jaffa.rpc.module.id:", e);
            throw new JaffaRpcNoRouteException(service, protocol.getShortName());
        }
    }

    public static void registerService(String service, Protocol protocol) {
        try {
            Stat stat = isZNodeExists("/" + service);
            if (Objects.nonNull(stat)) {
                update("/" + service, protocol);
            } else {
                create("/" + service, protocol);
            }
            services.add("/" + service);
            log.info("Registered service: {}", service);
        } catch (KeeperException | InterruptedException | UnknownHostException | ParseException e) {
            log.error("Can not register services in ZooKeeper", e);
            throw new JaffaRpcSystemException(e);
        }
    }

    public static Protocol getRpcProtocol() {
        return Protocol.getByName(Utils.getRequiredOption(Options.PROTOCOL));
    }

    public static Class<? extends Sender> getCurrentSenderClass() {
        Class<? extends Sender> sender = senders.get(getRpcProtocol());
        if (Objects.isNull(sender))
            throw new JaffaRpcSystemException(JaffaRpcSystemException.NO_PROTOCOL_DEFINED);
        else
            return sender;
    }

    public static int getServicePort() {
        int defaultPort = 4242;
        try {
            return Integer.parseInt(System.getProperty(Options.PROTOCOL_OPTION_PREFIX + getRpcProtocol().getShortName() + Options.SERVICE_PORT_OPTION_SUFFIX, String.valueOf(defaultPort)));
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    public static int getCallbackPort() {
        int defaultPort = 4342;
        try {
            return Integer.parseInt(System.getProperty(Options.PROTOCOL_OPTION_PREFIX + getRpcProtocol().getShortName() + Options.CALLBACK_PORT_OPTION_SUFFIX, String.valueOf(defaultPort)));
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    @SuppressWarnings("unchecked")
    private static void create(String service, Protocol protocol) throws KeeperException, InterruptedException, UnknownHostException {
        JSONArray ja = new JSONArray();
        ja.add(getServiceBindAddress(protocol));
        zk.create(service, ja.toJSONString().getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    private static Stat isZNodeExists(String service) throws KeeperException, InterruptedException {
        return zk.exists(service, true);
    }

    @SuppressWarnings("unchecked")
    private static void update(String service, Protocol protocol) throws KeeperException, InterruptedException, ParseException, UnknownHostException {
        byte[] zkData = zk.getData(service, true, null);
        JSONArray jArray = (JSONArray) new JSONParser().parse(new String(zkData));
        String local = getServiceBindAddress(protocol);
        if (!jArray.contains(local)) {
            jArray.add(local);
            zk.setData(service, jArray.toJSONString().getBytes(), zk.exists(service, true).getVersion());
        }
    }

    public static void deleteAllRegistrations(String service) throws KeeperException, InterruptedException, ParseException, UnknownHostException {
        for (Protocol protocol : Protocol.values()) {
            delete(service, protocol);
        }
    }

    public static void delete(String service, Protocol protocol) throws KeeperException, InterruptedException, ParseException, UnknownHostException {
        byte[] zkData = zk.getData(service, true, null);
        JSONArray jArray = (JSONArray) new JSONParser().parse(new String(zkData));
        String local = getServiceBindAddress(protocol);
        if (jArray.contains(local)) {
            jArray.remove(local);
            zk.setData(service, jArray.toJSONString().getBytes(), zk.exists(service, true).getVersion());
        }
        log.info("Service {} for protocol {} was unregistered", service, protocol.getFullName());
    }

    private static String getServiceBindAddress(Protocol protocol) throws UnknownHostException {
        return getLocalHostLANAddress().getHostAddress() + ":" + getServicePort() + "#" + Utils.getRequiredOption(Options.MODULE_ID) + "#" + protocol.getShortName();
    }

    public static String getZeroMQBindAddress() throws UnknownHostException {
        return getLocalHostLANAddress().getHostAddress() + ":" + getServicePort();
    }

    public static InetSocketAddress getHttpBindAddress() {
        return new InetSocketAddress(Utils.getLocalHost(), getServicePort());
    }

    public static String getZeroMQCallbackBindAddress() throws UnknownHostException {
        return getLocalHostLANAddress().getHostAddress() + ":" + getCallbackPort();
    }

    public static InetSocketAddress getHttpCallbackBindAddress() {
        return new InetSocketAddress(Utils.getLocalHost(), getCallbackPort());
    }

    public static String getHttpCallbackStringAddress() throws UnknownHostException {
        return getHttpPrefix() + getLocalHostLANAddress().getHostAddress() + ":" + getCallbackPort();
    }

    public static String getLocalHost() {
        try {
            return getLocalHostLANAddress().getHostAddress();
        } catch (UnknownHostException e) {
            throw new JaffaRpcSystemException(e);
        }
    }

    private static InetAddress getLocalHostLANAddress() throws UnknownHostException {
        try {
            InetAddress candidateAddress = null;
            for (Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                NetworkInterface iface = ifaces.nextElement();
                for (Enumeration<InetAddress> inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {
                        if (inetAddr.isSiteLocalAddress()) {
                            return inetAddr;
                        } else if (Objects.isNull(candidateAddress)) {
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            if (Objects.nonNull(candidateAddress)) {
                return candidateAddress;
            }
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (Objects.isNull(jdkSuppliedAddress)) {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return jdkSuppliedAddress;
        } catch (SocketException e) {
            UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}

class ShutdownHook extends Thread {
    @Override
    public void run() {
        try {
            if (Objects.nonNull(Utils.getConn())) {
                for (String service : Utils.getServices()) {
                    Utils.deleteAllRegistrations(service);
                }
                if (Objects.nonNull(Utils.getConn())) Utils.getConn().close();
            }
            Utils.setConn(null);
        } catch (KeeperException | InterruptedException | ParseException | IOException e) {
            throw new JaffaRpcSystemException(e);
        }
    }
}
