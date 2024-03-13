package mapdata;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
    private final ConcurrentHashMap<String, Lock> keyLocks = new ConcurrentHashMap<String, Lock>();
    private final static int MAX_STORE_LENGTH = 65000;
    private final String membershipServerAddr;
    private final static int MAX_TRANSACTION_ATTEMPTS = 10;
    private final int port;
    private ConcurrentHashMap<String, String> memberMap = new ConcurrentHashMap<String, String>();

    public ChangeImplementation(int port, String membershipServerAddr) {
        this.membershipServerAddr = membershipServerAddr;
        this.port = port;
        if (membershipServerAddr != null && membershipServerAddr.length() > 0){

        } else {
            updateMemberMapFromFile();

        }
    }

    @Override
    public String changeData(String valuestring, String type) {
        String[] parts = valuestring.split(" ");
        String operation = parts[0];
        String key = parts.length > 1 ? parts[1] : null;
        String value = parts.length > 2 ? parts[2] : null;
        StringBuilder response = new StringBuilder();

        switch (operation) {
            case "dput1":
                String dput1String = handlePutPhaseOne(key);
                response.append(dput1String);
                break;
            case "dput2":
                String dput2String = handlePutPhaseTwo(key, value, type);
                response.append(dput2String);
                break;
            case "dputabort":
                String dputabortString = handlePutAbort(key);
                response.append(dputabortString);
                break;
            case "put":
                int putAttempts = 0;
                while (putAttempts < MAX_TRANSACTION_ATTEMPTS) {
                    String putResult = handleClientPut(key, value, type, memberMap);
                    if (putResult.equals("put key=" + key)) {
                        response.append(putResult);
                        break;
                    } else {
                        putAttempts++;
                    }
                }
                if (putAttempts == MAX_TRANSACTION_ATTEMPTS) {
                    response.append("put key=").append(key).append(" aborted after ").append(putAttempts)
                            .append(" attempts");
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
                if (deleteAttempts == MAX_TRANSACTION_ATTEMPTS) {
                    response.append("delete key=").append(key).append(" aborted after ").append(deleteAttempts)
                            .append(" attempts");
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

    private void sendAbortToAllServers(String key, String operation) {
        memberMap.forEach((ip, port) -> {
            try (Socket socket = new Socket(ip, Integer.parseInt(port));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                if (operation.equals("put")) {
                    out.println("dputabort " + key);
                } else if (operation.equals("del")) {
                    out.println("ddelabort " + key);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private String handleClientPut(String key, String value, String serverType,
            ConcurrentHashMap<String, String> membershipMap) {
        Boolean abort = false;
        String res = "";
        String lockResult = handlePutPhaseOne(key);
        if (!lockResult.equals("abort")) {
            for (Map.Entry<String, String> entry : membershipMap.entrySet()) {
                String ip = entry.getKey();
                String port = entry.getValue();
                try {
                    // send dput1 message to ip:port
                    Socket socket = new Socket(ip, Integer.parseInt(port));
                    System.out.println("Sending dput1 message to " + ip + ":" + port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println("dput1 " + key);
                    // if any server responds with dputabort, then send dputabort message to all
                    // servers from the membershipMap
                    String serverAck = in.readLine();
                    if (serverAck.equals("abort")) {
                        abort = true;
                        res = "Put abort";
                        sendAbortToAllServers(key, "put");
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
        if (abort) {
            handlePutAbort(key);
            return res;
        } else {
            handlePutPhaseTwo(key, value, serverType);
            for (Map.Entry<String, String> entry : membershipMap.entrySet()) {
                String ip = entry.getKey();
                String port = entry.getValue();
                try {
                    // send dput2 message to ip:port
                    Socket socket = new Socket(ip, Integer.parseInt(port));
                    System.out.println("Sending dput2 message to " + ip + ":" + port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println("dput2 " + key + " " + value);
                    res = in.readLine();
                    socket.close();
                } catch (Exception e) {
                    res = "abort, but exceptions occurred while putting key=" + key + " value=" + value
                            + " to other servers.";
                    e.printStackTrace();
                }
            }
        }
        return res;
    }

    private String handlePutPhaseOne(String key) {
        String res = "";
        Lock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
        if (lock.tryLock()) {
            
            res = "ok " + key;
        } else {
            res = "abort";
        }
        return res;
    }

    private String handlePutPhaseTwo(String key, String value, String serverType) {
        String res = "";
        try {
            ConcurrentHashMap<String, String> map = getMap(serverType);
            map.put(key, value);
            res = "put key=" + key;
        } finally {
            Lock lock2 = keyLocks.get(key);
            if (lock2 != null) {
                lock2.unlock();
            }
        }       
        return res;
    }

    private String handlePutAbort(String key) {
        String res = "";
        Lock lockedObject = keyLocks.get(key);
        if (lockedObject instanceof ReentrantLock) {
            ReentrantLock lock = (ReentrantLock) lockedObject;
            if (lockedObject != null && lock.isHeldByCurrentThread()) {
                lockedObject.unlock();
                keyLocks.remove(key);
                res = "put key=" + key + " aborted";
            } else {
                res = "Current thread does not hold the lock for key=" + key;         
            }
        } else {
            res = "No transaction is found or already aborted for key=" + key;
        }
        return res;
    }

    private String handleClientDelete(String key, String serverType, ConcurrentHashMap<String, String> membershipMap) {
        Boolean abort = false;
        String res = "";
        // check key is locked locally, then send ddel1 message to all servers from the
        // membershipMap
        if (!handleDeletePhaseOne(key).equals("abort")) {
            for (Map.Entry<String, String> entry : membershipMap.entrySet()) {
                String ip = entry.getKey();
                String port = entry.getValue();
                try {
                    // send ddel1 message to ip:port
                    Socket socket = new Socket(ip, Integer.parseInt(port));
                    System.out.println("Sending ddel1 message to " + ip + ":" + port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println("ddel1 " + key);
                    // if any server responds with ddelabort, then send ddelabort message to all
                    // servers from the membershipMap
                    String serverAck = in.readLine();
                    if (serverAck.equals("abort")) {
                        abort = true;
                        System.out.println("Received abort message from " + ip + ":" + port);
                        res = "Delete abort";
                        sendAbortToAllServers(key, "del");
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
        if (abort) {
            handleDeleteAbort(key);
            return res;
        } else {
            handleDeletePhaseTwo(key, serverType);
            for (Map.Entry<String, String> entry : membershipMap.entrySet()) {
                String ip = entry.getKey();
                String port = entry.getValue();
                try {
                    // send ddel2 message to ip:port
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
        if (!keyLocks.containsKey(key)) {
            keyLocks.put(key, new ReentrantLock());
        }
        if (keyLocks.get(key).tryLock()) {
            System.out.println("Locking key=" + key);
            res = "ok " + key;
        } else {
            System.out.println("Received ddel1 message for key=" + key + ", locked by others, aborting");
            res = "abort";
        }
        return res;
    }

    private String handleDeletePhaseTwo(String key, String type) {
        String res = "";
        System.out.println("Received ddel2 message for key=" + key + " deleting");
        //keyLocks.get(key).unlock();
        getMap(type).remove(key);
        Lock lock = keyLocks.get(key);
        if (lock != null) {
            lock.unlock();
        }
        res = "delete key=" + key;
        return res;
    }

    private String handleDeleteAbort(String key) {
        String res = "";
        Lock lockedObject = keyLocks.get(key);
        if (lockedObject instanceof ReentrantLock) {
            ReentrantLock lock = (ReentrantLock) lockedObject;
            if (lockedObject != null && lock.isHeldByCurrentThread()) {
                lockedObject.unlock();
                keyLocks.remove(key);
                res = "delete key=" + key + " aborted";
            }
        }
        return res;
    }

    @Override
    public void updateTCPMembershipMap(String response) {
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                String[] parts = line.split(":");
                if (parts.length == 4 && parts[0].equals("key") && parts[2].equals("value")) {
                    memberMap.put(parts[1], parts[3]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMemberMapFromFile() {
        Thread updateThread = new Thread(() -> {
            while (true) {
                memberMap.clear();
                try (BufferedReader br = new BufferedReader(new FileReader("../tmp/nodes.cfg"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] addressAndPort = line.split(":");
                        String address = addressAndPort[0];
                        String sourcePort = addressAndPort[1];
                        String myIP = InetAddress.getLocalHost().getHostAddress();
                        if (!address.equals(myIP) || !sourcePort.equals(String.valueOf(this.port))) {
                            memberMap.put(address, sourcePort);
                            // print out the membership list
                            System.out.println("Membership list: " + memberMap);
                        }
                    }
                    
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
        } else {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

}
