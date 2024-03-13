package mapdata;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MembershipServer {
    private static final int MEMBERSHIP_SERVER_PORT = 4410;
    //private static ConcurrentHashMap<String, String> memberMap = new ConcurrentHashMap<>();

    public static void startMembershipServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Membership server started on port " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    String inputLine = in.readLine();
                    if (inputLine != null) {
                        String[] parts = inputLine.split(" ");
                        String command = parts[0];
                        handleCommand(command, parts, out);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error in membership server: " + e.getMessage());
        }
    }
    private static void handleCommand(String command, String[] parts, PrintWriter out) {
        switch (command) {
            case "put":
                String ip = parts[1];
                String port = parts[2];
                ChangeImplementation.memberMap.put(ip, port);
                out.println("put key=" + ip);
                break;
            case "get":
                String key = parts[1];
                String value = ChangeImplementation.memberMap.get(key);
                if (value != null) {
                    out.println("get key=" + key + " get value=" + value);
                } else {
                    out.println("get key=" + key + " not found");
                }
                break;
            case "del":
                String delKey = parts[1];
                if (ChangeImplementation.memberMap.remove(delKey) != null) {
                    out.println("delete key=" + delKey);
                } else {
                    out.println("delete key=" + delKey + " not found");
                }
                break;
            case "store":
                StringBuilder response = new StringBuilder();
                for (Map.Entry<String, String> entry : ChangeImplementation.memberMap.entrySet()) {
                    response.append("key:").append(entry.getKey())
                            .append(":value:").append(entry.getValue()).append("\n");
                }
                out.println(response.toString());
                break;
            case "exit":
                System.exit(0);
                break;
            default:
                out.println("Unknown command: " + command);
                break;
        }
    }
}