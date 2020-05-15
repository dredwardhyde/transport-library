package com.jaffa.rpc.lib.zookeeper;

import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

@Slf4j
public class Utils {

    @Getter
    private static final List<String> services = new ArrayList<>();
    @Getter
    @Setter
    private static volatile ZooKeeperConnection conn;
    private static ZooKeeper zk;

    public static void loadExternalProperties() {
        try {
            String path = System.getProperty("jaffa-rpc-config");
            if (path != null) {
                log.info("Loading Jaffa RPC properies from file {}", path);
                Properties p = new Properties();
                InputStream is = new FileInputStream(path);
                p.load(is);
                for (String name : p.stringPropertyNames()) {
                    String value = p.getProperty(name);
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
        if (optionValue == null || optionValue.trim().isEmpty())
            throw new IllegalArgumentException("Property " + option + "  was not set");
        else return optionValue;
    }

    public static String getServiceInterfaceNameFromClient(String clientName) {
        return clientName.replaceAll("Client$", "");
    }

    public static Pair<String, String> getHostForService(String service, String moduleId, Protocol protocol) {
        service = Utils.getServiceInterfaceNameFromClient(service);
        Stat stat;
        try {
            stat = isZNodeExists("/" + service);
        } catch (KeeperException | InterruptedException e) {
            log.error("Can not connect to ZooKeeper cluster", e);
            throw new JaffaRpcSystemException(e);
        }
        if (stat != null) {
            try {
                MutablePair<String, String> host = getHostsForService("/" + service, moduleId, protocol).get(0);
                if (protocol.equals(Protocol.HTTP)) {
                    host.left = getHttpPrefix() + host.left;
                }
                return host;
            } catch (KeeperException | ParseException | InterruptedException e) {
                throw new JaffaRpcNoRouteException(service);
            }
        } else throw new JaffaRpcNoRouteException(service);
    }

    private static String getHttpPrefix() {
        return (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.use.https", String.valueOf(false))) ? "https" : "http") + "://";
    }

    private static ArrayList<MutablePair<String, String>> getHostsForService(String service, String moduleId, Protocol protocol) throws KeeperException, ParseException, InterruptedException {
        byte[] zkData = zk.getData(service, false, null);
        JSONArray jArray = (JSONArray) new JSONParser().parse(new String(zkData));
        if (jArray.isEmpty())
            throw new JaffaRpcNoRouteException(service);
        else {
            ArrayList<MutablePair<String, String>> hosts = new ArrayList<>();
            for (Object json : jArray) {
                String[] params = ((String) json).split("#");
                if (moduleId != null) {
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
            byte[] zkData = zk.getData("/" + service, false, null);
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
        } catch (KeeperException | InterruptedException | ParseException e) {
            log.error("Error while getting avaiable jaffa.rpc.module.id:", e);
            throw new JaffaRpcNoRouteException(service, protocol.getShortName());
        }
    }

    public static void registerService(String service, Protocol protocol) {
        try {
            Stat stat = isZNodeExists("/" + service);
            if (stat != null) {
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
        return Protocol.getByName(Utils.getRequiredOption("jaffa.rpc.protocol"));
    }

    private static int getServicePort() {
        int defaultPort = 4242;
        try {
            return Integer.parseInt(System.getProperty("jaffa.rpc.protocol." + getRpcProtocol().getShortName() + ".service.port", String.valueOf(defaultPort)));
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    private static int getCallbackPort() {
        int defaultPort = 4342;
        try {
            return Integer.parseInt(System.getProperty("jaffa.rpc.protocol." + getRpcProtocol().getShortName() + ".callback.port", String.valueOf(defaultPort)));
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
        byte[] zkData = zk.getData(service, false, null);
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
        byte[] zkData = zk.getData(service, false, null);
        JSONArray jArray = (JSONArray) new JSONParser().parse(new String(zkData));
        String local = getServiceBindAddress(protocol);
        if (jArray.contains(local)) {
            jArray.remove(local);
            zk.setData(service, jArray.toJSONString().getBytes(), zk.exists(service, true).getVersion());
        }
        log.info("Service {} for protocol {} was unregistered", service, protocol.getFullName());
    }

    private static String getServiceBindAddress(Protocol protocol) throws UnknownHostException {
        return getLocalHostLANAddress().getHostAddress() + ":" + getServicePort() + "#" + Utils.getRequiredOption("jaffa.rpc.module.id") + "#" + protocol.getShortName();
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
                        } else if (candidateAddress == null) {
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress;
            }
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
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
            if (Utils.getConn() != null) {
                for (String service : Utils.getServices()) {
                    Utils.deleteAllRegistrations(service);
                }
                if (Utils.getConn() != null) Utils.getConn().close();
            }
            Utils.setConn(null);
        } catch (KeeperException | InterruptedException | ParseException | IOException e) {
            throw new JaffaRpcSystemException(e);
        }
    }
}
