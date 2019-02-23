package org.openhab.binding.venstarthermostat.internal.discovery;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DiscoveryService.class, immediate = true, configurationPid = "binding.venstarthermostat")
public class VenstarThermostatDiscoveryService extends AbstractDiscoveryService {
    private final Logger log = LoggerFactory.getLogger(VenstarThermostatDiscoveryService.class);
    private static final String COLOR_TOUCH_DISCOVERY_MESSAGE = "M-SEARCH * HTTP/1.1\r\n"
            + "Host: 239.255.255.250:1900\r\n" + "Man: ssdp:discover\r\n" + "ST: colortouch:ecp\r\n" + "\r\n";
    private static final Pattern USN_PATTERN = Pattern
            .compile("^colortouch:ecp((?::[0-9a-fA-F]{2}){6}):name:(.+)(?::type:(\\w+))");
    private static final String SSDP_MATCH = "colortouch:ecp";
    private static final int BACKGROUND_SCAN_INTERVAL = 300; // seconds
    // private DiscoveryServiceCallback discoveryServiceCallback;
    private ScheduledFuture<?> scheduledFuture;

    public VenstarThermostatDiscoveryService() {
        super(VenstarThermostatBindingConstants.SUPPORTED_THING_TYPES, 30, true);
    }

    @Override
    protected void startBackgroundDiscovery() {
        log.debug("Starting Background Scan");
        stopBackgroundDiscovery();
        scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            doRunRun();
        }, 0, BACKGROUND_SCAN_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    protected void stopBackgroundDiscovery() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(true);
        }
    }

    @Override
    protected void startScan() {
        log.info("Starting Interactive Scan");
        doRunRun();
    }

    // @Override
    // public void setDiscoveryServiceCallback(DiscoveryServiceCallback discoveryServiceCallback) {
    // this.discoveryServiceCallback = discoveryServiceCallback;
    // }

    protected synchronized void doRunRun() {
        log.debug("Sending SSDP discover.");
        for (int i = 0; i < 5; i++) {
            try {
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                while (nets.hasMoreElements()) {
                    NetworkInterface ni = nets.nextElement();
                    MulticastSocket socket = sendDiscoveryBroacast(ni);
                    if (socket != null) {
                        scanResposesForKeywords(socket);
                    }
                }
            } catch (IOException e) {
                log.debug("Error discoverying devices", e);
            }
        }
    }

    /**
     * Broadcasts a SSDP discovery message into the network to find provided
     * services.
     *
     * @return The Socket the answers will arrive at.
     * @throws UnknownHostException
     * @throws IOException
     * @throws SocketException
     * @throws UnsupportedEncodingException
     */
    private MulticastSocket sendDiscoveryBroacast(NetworkInterface ni)
            throws UnknownHostException, SocketException, UnsupportedEncodingException {
        InetAddress m = InetAddress.getByName("239.255.255.250");
        final int port = 1900;

        log.debug("Considering {}", ni.getName());
        try {
            if (!ni.isUp() || !ni.supportsMulticast()) {
                log.debug("skipping interface {}", ni.getName());
                return null;
            }

            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            InetAddress a = null;
            while (addrs.hasMoreElements()) {
                a = addrs.nextElement();
                if (a instanceof Inet4Address) {
                    break;
                } else {
                    a = null;
                }
            }
            if (a == null) {
                log.debug("no ipv4 address on " + ni.getName());
                return null;
            }

            // for whatever reason, the venstar thermostat responses will not be seen
            // if we bind this socket to a particular address.
            // this seems to be okay on linux systems, but osx apparently prefers ipv6, so this
            // prevents responses from being received unless the ipv4 stack is given preference.
            MulticastSocket socket = new MulticastSocket(new InetSocketAddress(port));
            socket.setSoTimeout(2000);
            socket.setReuseAddress(true);
            // log.debug("Network Interface: " + ni.getName());
            socket.setNetworkInterface(ni);
            socket.joinGroup(m);

            log.trace("Joined UPnP Multicast group on Interface: " + ni.getName());
            byte[] requestMessage = COLOR_TOUCH_DISCOVERY_MESSAGE.getBytes("UTF-8");
            DatagramPacket datagramPacket = new DatagramPacket(requestMessage, requestMessage.length, m, port);
            socket.send(datagramPacket);
            return socket;
        } catch (IOException e) {
            log.debug("got ioexception: " + e.getMessage());
        }

        return null;
    }

    /**
     * Scans all messages that arrive on the socket and scans them for the
     * search keywords. The search is not case sensitive.
     *
     * @param socket
     *                     The socket where the answers arrive.
     * @param keywords
     *                     The keywords to be searched for.
     * @return
     * @throws IOException
     */
    private void scanResposesForKeywords(MulticastSocket socket, String... keywords) throws IOException {
        // In the worst case a SocketTimeoutException raises
        do {
            byte[] rxbuf = new byte[8192];
            DatagramPacket packet = new DatagramPacket(rxbuf, rxbuf.length);
            try {
                socket.receive(packet);
            } catch (Exception e) {
                log.debug("Got exception while trying to receive UPnP packets: " + e.getMessage());
                return;
            }
            String response = new String(packet.getData());
            if (response.contains(SSDP_MATCH)) {
                log.trace("Match: {} ", response);
                parseResponse(response);
            }
        } while (true);
    }

    protected void parseResponse(String response) {
        DiscoveryResult result;

        String name = null;
        String url = null;
        String uuid = null;

        Scanner scanner = new Scanner(response);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] pair = line.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].toLowerCase();
            String value = pair[1].trim();
            log.trace("key: {} value: {}.", key, value);
            switch (key) {
                case "location":
                    url = value;
                    break;
                case "usn":
                    Matcher m = USN_PATTERN.matcher(value);
                    if (m.find()) {
                        uuid = m.group(1);
                        name = m.group(2);
                    }
                    break;
                default:
                    break;
            }
        }
        scanner.close();

        log.trace("Found thermostat, name: {} uuid: {} url: {}", name, uuid, url);

        if (name == null || uuid == null || url == null) {
            log.trace("Bad Format from thermostat");
            return;
        }

        uuid = uuid.replace(":", "").toLowerCase();

        ThingUID thingUid = new ThingUID(VenstarThermostatBindingConstants.THING_TYPE_COLOR_TOUCH, uuid);

        log.debug("Got discovered device.");

        // Thing thing = getExistingThing(thingUid);
        // if (thing != null) {
        // log.debug("Already have thing with ID=<{}>", thingUid);
        // String thingUrl = thing.getProperties().get(VenstarThermostatBindingConstants.PROPERTY_URL);
        // log.debug("ThingURL=<{}>, discoveredUrl=<{}>", thingUrl, url);
        // if (thingUrl == null || !thingUrl.equals(url)) {
        // ((VenstarThermostatHandler) thing.getHandler()).updateUrl(url);
        // thing.getHandler().thingUpdated(thing);
        // log.info("Updated url for existing Thermostat => {}", url);
        // }
        // return;
        // } else {
        // log.debug("New Device, adding to inbox");
        // }

        String label = String.format("Venstar Thermostat (%s)", name);
        result = DiscoveryResultBuilder.create(thingUid).withLabel(label).withRepresentationProperty(uuid)
                .withProperty(VenstarThermostatBindingConstants.PROPERTY_UUID, uuid)
                .withProperty(VenstarThermostatBindingConstants.PROPERTY_URL, url).build();
        log.debug("New venstar thermostat discovered with ID=<{}>", uuid);
        this.thingDiscovered(result);
    }

    // public Thing getExistingThing(ThingUID thingUid) {
    // if (discoveryServiceCallback != null) {
    // return discoveryServiceCallback.getExistingThing(thingUid);
    // }
    // return null;
    // }
}
