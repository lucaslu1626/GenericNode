package mapdata;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeImplementation implements ChangeInterface {
    private final ConcurrentHashMap<String, Lock> keyLocks = new ConcurrentHashMap<>();
    private final static int MAX_STORE_LENGTH = 65000;
    private final static String MEMBERSHIP_SERVER_ADDR = "";
    private final static int MEMBERSHIP_SERVER_PORT = 4410;
    private final static int MAX_TRANSACTION_ATTEMPTS = 10;
    private Map<String, String> memberMap = new HashMap<>();
    private String addr;
    private int port;

    public ChangeImplementation(int port) {
        super();
        this.port = port;
        updateMemberMapFromFile();
    }

    @Override
    public String changeData(String valuestring, String type) {
        String[] parts = valuestring.split(" ");
        String operation = parts[0];
        String key = parts.length > 1 ? parts[1] : null;
        String value = parts.length > 2 ? parts[2] : null;
        StringBuilder response = new StringBuilder();
        //memberMap.put("127.0.0.1", "1235");
        switch (operation) {
            case "membership":
                // print the membership list
                response.append("Membership list: ");
                for (String ip : memberMap.keySet()) {
                    response.append(ip).append(":").append(memberMap.get(ip)).append(" ");
                }
                break;
            case "dput1":
                Lock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
                if (lock.tryLock()) {
                    response.append("Acknowledgement: key=").append(key).append(" is locked for dput1 and ready to proceed to commit the PUT operation");
                } else {
                    response.append("Abort: key=").append(key).append(" is already locked locally and the transaction will be aborted");
                }
                break;
            case "dput2":
                try {
                    ConcurrentHashMap<String, String> map = getMap(type);
                    map.put(key, value);
                    response.append("put key=").append(key);
                } finally {
                    Lock lock2 = keyLocks.get(key);
                    if (lock2 != null) {
                        lock2.unlock();
                    }
                }
                break;
            case "dputabort":
                Lock genericLock = keyLocks.get(key);
                if (genericLock instanceof ReentrantLock) {
                    ReentrantLock lock3 = (ReentrantLock) genericLock;
                    if (lock3.isHeldByCurrentThread()) {
                        lock3.unlock();
                        keyLocks.remove(key);
                        response.append("Abort: key=").append(key).append(" is unlocked and the transaction is aborted");
                    } else {
                        response.append("Current thread does not hold the lock for key=").append(key);
                    }
                } else {
                    response.append("No transaction is found or already aborted for key=").append(key);
                }           
                break;
            case "put":
                keyLocks.computeIfAbsent(key, k -> new ReentrantLock()).lock();
                try {
                    ConcurrentHashMap<String, String> map = getMap(type);
                    map.put(key, value);
                    response.append("put key=").append(key);
                } finally {
                    keyLocks.get(key).unlock();
                    //keyLocks.remove(key);
                }
                break;
            case "get":
                response.append("get key=").append(key).append(" get val=").append(getMap(type).get(key));
                break;
            case "del":
                int deleteAttempts = 0;
                while (deleteAttempts < MAX_TRANSACTION_ATTEMPTS) {
                    String deleteResult = handleClientDelete(key, type, memberMap);
                    if (deleteResult.equals("delete key=" + key)) {
                        response.append(deleteResult);
                        break;
                    } else {
                        deleteAttempts++;
                    }
                }
                if (deleteAttempts  == MAX_TRANSACTION_ATTEMPTS) {
                    response.append("delete key=").append(key).append(" aborted after ").append(deleteAttempts).append(" attempts");
                }
                break;
            case "ddel1":
                String ddel1Result = handleDeletePhaseOne(key);
                response.append(ddel1Result);
                break;
            case "ddel2":
                String ddel2Result = handleDeletePhaseTwo(key, type);
                response.append(ddel2Result);
                break;
            case "ddelabort":
                String ddelabortResult = handleDeleteAbort(key);
                response.append(ddelabortResult);
                break;
            case "store":
                List<String> entries = new ArrayList<>();
                Map<String, String> currentMap = getMap(type);
                currentMap.forEach((k, v) -> {
                    entries.add("key:" + k + ":value:" + v);
                });
                for (String entry : entries) {
                    if (response.length() + entry.length() + "\n".length() <= MAX_STORE_LENGTH) {
                        response.append(entry).append("\n");
                    } else {
                        response = new StringBuilder("TRIMMED:")
                                .append(response.substring(0, MAX_STORE_LENGTH - "TRIMMED:".length()));
                        break;
                    }
                }
                break;
            case "exit":
                response.append("server shutting down");
                System.exit(0);
                break;
            }
        return response.toString();
    }
    private void sendAbortToAllServers (String key) {
        for (String ip : memberMap.keySet()) {
            try {
                String port = memberMap.get(ip);
                // send ddelabort message to ip:port
                Socket socket = new Socket(ip, Integer.parseInt(port));
                System.out.println("Sending ddelabort message to " + ip + ":" + port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println("ddelabort " + key);
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private String handleClientDelete(String key, String serverType, Map<String, String> membershipMap) {
        // for each ip address, port in the membership list send ddel1 message
        // getMap(type).remove(key);
        //response.append("delete key=").append(key);
        Boolean abort = false;
        String res = "";
        // check key is locked locally, then send ddel1 message to all servers from the membershipMap
        if(!handleDeletePhaseOne(key).equals("abort")) {
            for (String ip : membershipMap.keySet()) {
                try {
                    // send ddel1 message to ip:port
                    String port = membershipMap.get(ip);
                    // send ddel1 message to ip:port
                    Socket socket = new Socket(ip, Integer.parseInt(port));
                    System.out.println("Sending ddel1 message to " + ip + ":" + port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println("ddel1 " + key);
                    //if any server responds with ddelabort, then send ddelabort message to all servers from the membershipMap
                    String serverAck = in.readLine();
                    if (serverAck.equals("abort")) {
                        abort = true;
                        System.out.println("Received abort message from " + ip + ":" + port);
                        res = "Delete abort";
                        sendAbortToAllServers(key);
                    } 
                    
                    System.out.println(serverAck.equals("ok " + key));
                    
                    socket.close();
                } catch (Exception e) {
                    abort = true;
                    e.printStackTrace();
                    break;
                }
            }
        } else {
            abort = true;
            res = "abort";
        }
        //if all servers respond with ok, delete <key, value> locally,
        // then send ddel2 message to all servers from the membershipMap 
        if (!abort) {
            handleDeletePhaseTwo(key, serverType);
            for (String ip : membershipMap.keySet()) {
                try {
                    // send ddel1 message to ip:port
                    String port = membershipMap.get(ip);
                    // send ddel1 message to ip:port
                    Socket socket = new Socket(ip, Integer.parseInt(port));
                    System.out.println("Sending ddel2 message to " + ip + ":" + port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println("ddel2 " + key);
                    res = in.readLine();
                    socket.close();
                } catch (Exception e) {
                    res = "abort, but exceptions occurred while deleting key=" + key + " from other servers.";
                    e.printStackTrace();
            
                }
            }
        }
        return res;
    }

    private String handleDeletePhaseOne(String key) {
        String res = "";
        System.out.println("Received ddel1 message for key=" + key);
        if (keyLocks.containsKey(key) && keyLocks.get(key).tryLock()){
            
            System.out.println("Locking key=" + key);
            //keyLocks.computeIfAbsent(key, k -> new ReentrantLock()).lock();
            res = "ok " + key;
        } else {
            // abort the delete
            System.out.println("Received ddel1 message for key=" + key + ", locked by others, aborting");
            res = "abort";
        }

        return res;
    }

    private String handleDeletePhaseTwo(String key, String type) {
        String res = "";
        System.out.println("Received ddel2 message for key=" + key + " deleting");
        keyLocks.get(key).unlock();
        getMap(type).remove(key);

        keyLocks.remove(key);
        res = "delete key=" +key;

        return res;
    }

    private String handleDeleteAbort(String key) {
        String res = "";
        Lock lockedObject = keyLocks.get(key);
        if (keyLocks.containsKey(key)){
            System.out.println("Received ddelabort message for key=" + key + " unlocking");
            lockedObject.unlock();
        }
        res = "delete key=" +key +" aborted";
        return res;

    }

    private void updateMemberMapFromFile() {
        Thread updateThread = new Thread(() -> {
            while (true) {
                memberMap.clear();
                try {
                    // Read membership list from file: /tmp/nodes.cfg
                    BufferedReader reader = new BufferedReader(new FileReader("../tmp/nodes.cfg"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Process each line of the file
                        // Update memberMap accordingly
                        String[] addressAndPort = line.split(":");
                        String address = addressAndPort[0];
                        String port = addressAndPort[1];
                        InetAddress myAddress = InetAddress.getLocalHost();
                        String myIp = myAddress.getHostAddress();
                        System.out.println("My IP: " + myIp + " My Port: " + this.port);
                        if (!address.equals(myIp) || !port.equals(Integer.toString(this.port))) {
                            System.out.println("Adding " + address + ":" + port + " to memberMap");
                            memberMap.put(address, port);
                        }
                    }
                    reader.close();
                    
                    Thread.sleep(10000);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }
        });
        updateThread.start();
    }

    private ConcurrentHashMap<String, String> getMap(String type) {
        if ("tcp".equals(type)) {
            return Mapdata.tcpmap;
        } else if ("udp".equals(type)) {
            return Mapdata.udpmap;
        } else {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

}
