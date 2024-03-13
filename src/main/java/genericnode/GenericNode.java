/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package genericnode;

import mapdata.ChangeImplementation;
import mapdata.ChangeInterface;
import mapdata.MembershipServer;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * @author wlloyd
 */
public class GenericNode {

    private static final int MEMBERSHIP_SERVER_PORT = 4410;
    public static String serviceString = "ChangeService";
    public static ChangeInterface change;

    public static void registerWithTCPMembershipServer(String membershipServerIP, int membershipServerPort, int membershipPort) {
        try (Socket socket = new Socket(membershipServerIP, membershipServerPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("put " + Inet4Address.getLocalHost().getHostAddress() + " " + membershipPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void fetchNodeList(String membershipServerIP, int membershipServerPort,
            ChangeInterface changeServer) {
        try (Socket socket = new Socket(membershipServerIP, membershipServerPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("store");
            String response;
            while ((response = in.readLine()) != null) {
                changeServer.updateTCPMembershipMap(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            if (args[0].equals("tc")) {
                System.out.println("TCP CLIENT");
                String addr = args[1];
                int port = Integer.parseInt(args[2]);
                String cmd = args[3];
                String key = (args.length > 4) ? args[4] : "";
                String val = (args.length > 5) ? args[5] : "";
                SimpleEntry<String, String> se = new SimpleEntry<String, String>(key, val);
                // insert code to make TCP client request to server at addr:port
                Socket socket = new Socket(addr, port);
                try {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println(cmd + " " + key + " " + val);
                    String response = "";
                    while ((response = in.readLine()) != null) {
                        System.out.println(response);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    socket.close();
                }
            } else if (args[0].equals("ts")) {
                int port = Integer.parseInt(args[1]);
                if (args.length > 2) {
                    String membershipServerIP = args[2];
                    int membershipServerPort = MEMBERSHIP_SERVER_PORT;
                    ChangeInterface changeServer = new ChangeImplementation(membershipServerIP);
                    registerWithTCPMembershipServer(membershipServerIP, membershipServerPort, port);
                    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(
                            () -> fetchNodeList(membershipServerIP, membershipServerPort, changeServer), 0, 10,
                            TimeUnit.SECONDS);
                    startTCPServer(port, changeServer);
                } else {
                    MembershipServer.startMembershipServer(port);
                }
            }
        } else {
            System.out.println("Missing membership server IP");
            System.exit(1);
        }
    }

    private static void startTCPServer(int port, ChangeInterface changeServer) {
        System.out.println("TCP SERVER");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                        OutputStream out = clientSocket.getOutputStream();
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(clientSocket.getInputStream()))) {
                    String inputLine = in.readLine();
                    if (inputLine != null) {
                        String message = changeServer.changeData(inputLine, "tcp") + "\n";
                        out.write(message.getBytes());
                        out.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
