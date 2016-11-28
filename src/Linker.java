import messages.Message;
import messages.MessageType;
import services.ServiceType;
import util.ConfigReader;
import util.MachineAddress;
import util.MachineType;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Linker are the bridge between clients and service
 * Linkers addresses are known from the clients and the services.
 * <p>
 * Linker communicate to clients with messages (Message)
 * <p>
 * When a linker is halt or stopped by error, the linker automatically tries to restart
 * <p>
 * Linker cannot be added after the initialization
 * <p>
 * Client can send
 * REQUEST_SERVICE
 * - Send the service address
 * <p>
 * NOT_RESPONDING_SERVICE
 * - Check if the service is dead and remove the service from the list
 * - If true: Send his table to the other linkers
 */
public class Linker {

    private final DatagramSocket socket;

    private List<MachineAddress> linkers;

    private Map<ServiceType, Set<MachineAddress>> services = new HashMap<>();

    public Linker(final int port, List<MachineAddress> linkers) throws SocketException {
        socket = new DatagramSocket(port);
        this.linkers = linkers;
    }

    /**
     * Handle register service request
     *
     * @param message
     * @param packet
     * @throws IOException
     */
    private void handleRegisterService(Message message, DatagramPacket packet) throws IOException {
        ServiceType serviceType = ServiceType.values()[message.getPayload()[0]];

        System.out.println("> Register service (" + serviceType + ")");

        // If the set is empty, we create the new set
        if (!services.containsKey(serviceType)) {
            Set<MachineAddress> set = new HashSet<>();
            services.put(serviceType, set);

        }
        String serviceHost = packet.getAddress().getHostAddress();
        int servicePort = packet.getPort();
        // We add the machine to the set
        services.get(serviceType).add(
                new MachineAddress(
                        packet.getAddress().getHostAddress(),
                        packet.getPort()
                ));

        System.out.println("[i] Services:");
        printServices();
        // Send an ACK to show that the linker is alive
        packet.setData(new Message(
                MessageType.ACK,
                MachineType.LINKER,
                null
        ).toByteArray());

        socket.send(packet);

        // Send service to other linkers
//        byte[] buff = new byte[512];
//        for (MachineAddress linkerAddress : linkers) {
//            DatagramPacket linkerPacket = new DatagramPacket(buff, buff.length, linkerAddress.getAddress(), linkerAddress.getPort());
//            linkerPacket.setData(new Message(
//                    MessageType.REGISTER_SERVICE_FROM_LINKER,
//                    MachineType.LINKER,
//                    new String(serviceHost + servicePort).getBytes() // THIS IS WRONG, SHOULD SEND SERVICETYPE + SERVICEADDRESS
//            ).toByteArray()
//            );
//            socket.send(linkerPacket);
//        }
    }

    private void handleAddService(Message message, DatagramPacket packet) {
        ServiceType serviceType = ServiceType.values()[message.getPayload()[0]];

        // If the set is empty, we create the new set
        if (!services.containsKey(serviceType)) {
            Set<MachineAddress> set = new HashSet<>();
            services.put(serviceType, set);

        }

        // We add the machine to the set
        services.get(serviceType).add(
                new MachineAddress(
                        packet.getAddress().getHostAddress(),
                        packet.getPort()
                ));

        printServices();
    }


    /**
     * Handle request service request
     *
     * @param message
     * @param packet
     * @throws IOException
     */
    private void handleRequestService(Message message, DatagramPacket packet) throws IOException {
        ServiceType serviceType = ServiceType.values()[message.getPayload()[0]];

        if (!services.isEmpty()) {
            System.out.println("> A client asked for a service");
            if (services.containsKey(serviceType)) {
                System.out.println(serviceType);

                Set<MachineAddress> specificServices = services.get(serviceType);

                System.out.println("[i] There is currently " + specificServices.size() + " services of " + serviceType.name());

                MachineAddress randomService = getAny(specificServices);

                // Send the address of one of the specific service
                packet.setData(new Message(
                        MessageType.RESPONSE,
                        MachineType.LINKER,
                        randomService.toByteArray()
                ).toByteArray());

                System.out.println("[i] Send service address to client");
                socket.send(packet);
            }
        }
    }

    private void warnOtherLinkers(MachineAddress serviceDownMachineAddress, Message message, DatagramPacket packet) throws IOException {
        byte[] buff = new byte[512];

        for (MachineAddress linker : linkers) {
            if (!linker.getAddress().equals(InetAddress.getLocalHost())
                    || linker.getPort() != socket.getLocalPort()) {
                packet = new DatagramPacket(buff, buff.length, linker.getAddress(), linker.getPort());

                packet.setData(new Message(
                        MessageType.REMOVE_SERVICE,
                        MachineType.LINKER,
                        serviceDownMachineAddress.toString().getBytes()).toByteArray()
                );

                socket.send(packet);
            } else {
                System.out.println("OURSELVE");
            }
        }
    }

    private void handleRemoveService(Message message, DatagramPacket packet) {
        try {
            MachineAddress service = MachineAddress.fromByteArray(message.getPayload());
            removeService(service);
            printServices();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send new list of services to linkers
     *
     * @param message
     * @param packet
     * @throws IOException
     */
    private void handleServiceDown(Message message, DatagramPacket packet) throws IOException {
        System.out.println(">>> SERVICE DOWN");

        // Send an ACK to the client to show that we received the request
        packet.setData(new Message(
                MessageType.ACK,
                MachineType.LINKER,
                null
        ).toByteArray());
        socket.send(packet);

        try {
            MachineAddress possibleDeadService = MachineAddress.fromByteArray(message.getPayload());

            byte[] buff = new byte[512];
            DatagramPacket tempPacket = new DatagramPacket(
                    buff, buff.length, possibleDeadService.getAddress(), possibleDeadService.getPort());

            tempPacket.setData(new Message(
                    MessageType.PING,
                    MachineType.LINKER,
                    null
            ).toByteArray());

            socket.setSoTimeout(1000);
            try {
                socket.send(tempPacket);

                socket.receive(tempPacket);

            } catch (SocketTimeoutException socketEx) {
                // Service is down
                System.out.println("Service is down indeed");

                removeService(possibleDeadService);

                warnOtherLinkers(possibleDeadService, message, packet);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[i] Error, invalid packet");
            return;
        }
    }

    /**
     * Remove the given service from the list
     *
     * @param deadService
     */
    private void removeService(MachineAddress deadService) {
        services.forEach((a, list) -> {
            if (list.contains(deadService)) {
                list.remove(deadService);
            }
        });

        System.out.println("[i] Updated services list");
        printServices();
    }

    /**
     * Listen for new messages from clients or services
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void listen() throws IOException, ClassNotFoundException {
        byte[] buff = new byte[1024];

        DatagramPacket packet;

        Message message;

        // Listen for new messages
        System.out.println("[i] Listen for new messages (on " + socket.getLocalSocketAddress() + ")...");

        while (true) {
            // Reset packet before reuse
            packet = new DatagramPacket(buff, buff.length);
            socket.receive(packet);

            // Continue, even if the packet is corrupt and cannot be unserialized
            try {
                message = Message.fromByteArray(buff);
            } catch (EOFException e) {
                System.out.println("[i] Message could not be decoded !");
                e.printStackTrace();
                continue;
            }

            // DEBUG
            System.out.println("New message [" + packet.getAddress().getHostName() + ":" + packet.getPort() + "]");
            System.out.println(message);

            switch (message.getMessageType()) {
                case REGISTER_SERVICE:
                    handleRegisterService(message, packet);
                    break;
                case REGISTER_SERVICE_FROM_LINKER:
                    handleAddService(message, packet);
                    break;
                case REQUEST_SERVICE:
                    handleRequestService(message, packet);
                    break;
                case SERVICE_DOWN:
                    handleServiceDown(message, packet);
                    break;
                case REMOVE_SERVICE:
                    handleRemoveService(message, packet);
                    break;
                default:
                    System.out.println("> Got an unknown message !");
            }
        }
    }

    public static MachineAddress getAny(final Set<MachineAddress> services) {
        int num = (int) (Math.random() * services.size());
        for (MachineAddress ma : services) if (--num < 0) return ma;
        throw new AssertionError();
    }

    private void printServices() {
        services.forEach((m, a) -> System.out.println("- " + a));
    }

    /**
     * Run a linker on a specific port from the command line
     *
     * @param args
     */
    public static void main(String... args) {
        System.out.println("- Linker -");

        if (args.length < 1) {
            System.out.println("Usage: java linker <linker id>");
            System.out.println("Note: <linker id> is the line number in linkers.txt");
            return;
        }

        final int id = Integer.parseInt(args[0]);

        try {
            List<MachineAddress> linkers = ConfigReader.read(new File("linkers.txt"));
            MachineAddress config = linkers.get(id);

            Linker linker = new Linker(config.getPort(), linkers);
            linker.listen();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
