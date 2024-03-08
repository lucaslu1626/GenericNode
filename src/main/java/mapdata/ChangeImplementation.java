package mapdata;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeImplementation extends UnicastRemoteObject implements ChangeInterface {
    private final ConcurrentHashMap<String, Lock> keyLocks = new ConcurrentHashMap<>();
    private final static int MAX_STORE_LENGTH = 65000;

    public ChangeImplementation() throws RemoteException {
        super();
    }

    @Override
    public String changeData(String valuestring, String type) throws RemoteException {
        String[] parts = valuestring.split(" ");
        String operation = parts[0];
        String key = parts.length > 1 ? parts[1] : null;
        String value = parts.length > 2 ? parts[2] : null;
        StringBuilder response = new StringBuilder();

        switch (operation) {
            case "dput1":
                //Placeholders for the dput1 operations
                keyLocks.computeIfAbsent(key, k -> new ReentrantLock()).lock();
                try {
                    ConcurrentHashMap<String, String> map = getMap(type);
                    map.put(key, value);
                    response.append("put key=").append(key);
                } finally {
                    keyLocks.get(key).unlock();
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
                }
                break;
            case "get":
                response.append("get key=").append(key).append(" get val=").append(getMap(type).get(key));
                break;
            case "del":
                // for each ip address, port in the membership list send ddel1 message
                // getMap(type).remove(key);
                //response.append("delete key=").append(key);
                break;
            case "ddel1":
                if (keyLocks.containsKey(key)) {
                    // abort the delete
                    response.append("ddelabort");
                }
                keyLocks.computeIfAbsent(key, k -> new ReentrantLock()).lock();
                break;
            case "ddel2":
                getMap(type).remove(key);
                keyLocks.get(key).unlock();
                keyLocks.remove(key);
                response.append("delete key=").append(key);
                break;
            case "ddelabort":
                keyLocks.get(key).unlock();
                response.append("delete key=").append(key).append(" aborted");
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
