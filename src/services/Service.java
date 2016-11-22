package services;

import messages.Message;
import util.Machine;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static messages.Message.TIME;

/**
 * At initialization, a service will register himself to a random linker (sending him the host,
 * port and type of the service).
 * Then, the service will just listen for new messages and reply.
 *
 * Multiple types of services exists.
 * Automatically restart on error.
 */
public abstract class Service {

    private final int PORT;

    private List<Machine> linkers;

    public Service(List<Machine> linkers, final int port) {
        this.linkers = linkers;
        this.PORT = port;
    }

    /**
     * At initialization, service need to register himself to one of the linkers
     */
    int subscribeToLinker() throws IOException {
        linkers.forEach(System.out::println);

        // Use a random linker in the list
        Machine linker = linkers.get((int) Math.random() * linkers.size());

        byte[] buff = new Message(Message.REGISTER).toByteArray();

        InetAddress address = InetAddress.getByName(linker.getHost());
        DatagramPacket packet = new DatagramPacket(buff, buff.length, address, linker.getPort());

        DatagramSocket ds = new DatagramSocket(PORT);
        ds.send(packet);
        ds.close();

        return ds.getPort();
//        ds.send(new DatagramPacket(new MessageServiceRegistration(type, host, port)));
    }

    /**
     * Listen for incoming message
     */
    void listen() throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);

        byte[] buff = new byte[Long.BYTES];

        DatagramPacket packet = new DatagramPacket(buff, buff.length);

        Message message;

        System.out.println("[i] Listen for new messages...");

        // Listen for new messages
        while (true) {
            socket.receive(packet);

            byte type = buff[0];
            byte[] data = Arrays.copyOfRange(buff, 1, buff.length);
            message = new Message(type, data);

            // DEBUG
            System.out.println("New message from " + packet.getAddress().getHostName() + ":" + packet.getPort());
            System.out.println("(message: " + message.getMessage() + ")");

            switch (message.getType()) {
                case TIME:
                    System.out.println(">>> ASK FOR THE SERVICE RESPONSE");
                    type = this.getServiceType();
                    buff = this.getResponse();
                    message = new Message(type, data);
                    sendMessage(packet, message);
                    break;
                default:
                    System.out.println(">>> Unknown message");
            }

            // Reset the length of the packet before reuse
            packet.setLength(buff.length);
        }
    }

    abstract byte[] getResponse();

    abstract byte getServiceType();

    /**
     * Send a message
     *
     * @param packet
     * @param message
     */
    private void sendMessage(DatagramPacket packet, Message message) throws IOException {
        DatagramPacket packetToSend = new DatagramPacket(
                message.getMessage(), message.getMessage().length, packet.getAddress(), packet.getPort());

        DatagramSocket ds = new DatagramSocket();
        ds.send(packetToSend);
        ds.close();
    }

    public static void main(String[] args) {
        System.out.println("- Service -");

        if (args.length < 2) {
            System.out.println("Usage: java service <type> <list of linkers>");
            return;
        }

        List<Machine> linkers = new ArrayList<>();
        String type = args[0];

        // 127.0.0.1:8080,127.0.0.1:8090
        for (String info : args[1].split(",")) {
            String[] token = info.split(":");
            linkers.add(new Machine(token[0], Integer.parseInt(token[1])));
        }

        Service service;
//        if (type.equals("reply"))
        service = new ServiceTime(linkers, 9911);

        try {
            int port = service.subscribeToLinker();
            service.listen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}