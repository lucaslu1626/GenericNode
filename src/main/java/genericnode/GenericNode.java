/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package genericnode;

import mapdata.ChangeImplementation;

import java.io.*;
import java.net.*;
import java.rmi.registry.Registry;
import java.util.AbstractMap.SimpleEntry;

/**
 * @author wlloyd
 */
public class GenericNode {
    public static Registry registry;
    public static String serviceString = "ChangeService";

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
            }
            if (args[0].equals("ts")) {
                System.out.println("TCP SERVER");
                ChangeImplementation changeServer = new ChangeImplementation();
                int port = Integer.parseInt(args[1]);
                String message = null;
                // insert code to start TCP server on port
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    while (true) {
                        try (Socket clientSocket = serverSocket.accept();
                                OutputStream out = clientSocket.getOutputStream();
                                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                            String inputLine = in.readLine();
                            if (inputLine != null) {
                                message = changeServer.changeData(inputLine) + "\n";
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
        } else {
            String msg = "GenericNode Usage:\n\n" +
                    "Client:\n" +
                    "uc/tc <address> <port> put <key> <msg>  UDP/TCP CLIENT: Put an object into store\n" +
                    "uc/tc <address> <port> get <key>  UDP/TCP CLIENT: Get an object from store by key\n" +
                    "uc/tc <address> <port> del <key>  UDP/TCP CLIENT: Delete an object from store by key\n" +
                    "uc/tc <address> <port> store  UDP/TCP CLIENT: Display object store\n" +
                    "uc/tc <address> <port> exit  UDP/TCP CLIENT: Shutdown server\n" +
                    "rmic <address> put <key> <msg>  RMI CLIENT: Put an object into store\n" +
                    "rmic <address> get <key>  RMI CLIENT: Get an object from store by key\n" +
                    "rmic <address> del <key>  RMI CLIENT: Delete an object from store by key\n" +
                    "rmic <address> store  RMI CLIENT: Display object store\n" +
                    "rmic <address> exit  RMI CLIENT: Shutdown server\n\n" +
                    "Server:\n" +
                    "us/ts <port>  UDP/TCP SERVER: run udp or tcp server on <port>.\n" +
                    "rmis  run RMI Server.\n";
            System.out.println(msg);
        }

    }

}
