import messages.Message;
import util.Machine;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static messages.Message.REGISTER;

/**
 * Linker are the bridge between clients and service
 * because linkers IPs are known from the clients and the services.
 *
 * Linker communicate to clients with messages (Message)
 *
 * When a linker is halt or stopped by error, the linker automatically tries to restart
 *
 * Linker cannot be added after the initialization
 */
public class Linker {

    private final int PORT;

    private List<Machine> services = new ArrayList<>();

    private List<Machine> clients = new ArrayList<>();

    public Linker(final int port) {
        PORT = port;
    }

//    private void onRegistration(Message message) {
//    }

    /**
     * Listen for new messages from clients or services
     *
     * @throws IOException
     */
    public void listen() throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);

        byte[] buff = new byte[Long.BYTES];

        DatagramPacket packet = new DatagramPacket(buff, buff.length);

        // Listen for new messages
        while (true) {
            socket.receive(packet);

            byte type = buff[0];
            byte[] data = Arrays.copyOfRange(buff, 1, buff.length);
            Message message = new Message(type, data);

            // DEBUG
            System.out.println("New message from " + packet.getAddress().getHostName() + ":" + packet.getPort());
            System.out.println("(message: " + message.getMessage() + ")");

            switch (message.getType()) {
                case REGISTER:
                    System.out.println(">>> REGISTER");

                    // TODO check for doubles
                    services.add(new Machine(packet));

                    System.out.println("[i] Services:");
                    printServices();

                    break;
                default:
                    System.out.println(">>> Unknown message");
            }

            // Reset the length of the packet before reuse
            packet.setLength(buff.length);
        }
    }

    private void printServices() {
        services.forEach(System.out::println);
    }

    /**
     * Run a linker on a specific port from the command line
     *
     * @param args
     */
    public static void main(String... args) {
        System.out.println("- Linker -");

        if (args.length < 1) {
            System.out.println("Usage: java linker <port>");
            return;
        }

        final int port = Integer.parseInt(args[0]);

        try {
            Linker linker = new Linker(port);
            linker.listen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
