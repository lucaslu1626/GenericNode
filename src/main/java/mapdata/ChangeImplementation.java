package mapdata;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeImplementation extends UnicastRemoteObject {
    private final ConcurrentHashMap<String, Lock> keyLocks = new ConcurrentHashMap<>();
    private final static int MAX_STORE_LENGTH = 65000;
    private static ConcurrentHashMap<String,String> map = new ConcurrentHashMap();

    public ChangeImplementation() throws RemoteException {
        super();
    }

    public String changeData(String valuestring) throws RemoteException {
        String[] parts = valuestring.split(" ");
        String operation = parts[0];
        String key = parts.length > 1 ? parts[1] : null;
        String value = parts.length > 2 ? parts[2] : null;
        StringBuilder response = new StringBuilder();

        switch (operation) {
            case "dput1":
            //placeholder for dput1
                keyLocks.computeIfAbsent(key, k -> new ReentrantLock()).lock();
                try {
                    map.put(key, value);
                    response.append("put key=").append(key);
                } finally {
                    keyLocks.get(key).unlock();
                }
                break;
            case "put":
                keyLocks.computeIfAbsent(key, k -> new ReentrantLock()).lock();
                try {
                    map.put(key, value);
                    response.append("put key=").append(key);
                } finally {
                    keyLocks.get(key).unlock();
                }
                break;
            case "get":
                response.append("get key=").append(key).append(" get val=").append(map.get(key));
                break;
            case "del":
                map.remove(key);
                response.append("delete key=").append(key);
                break;
            case "store":
                List<String> entries = new ArrayList<>();
                map.forEach((k, v) -> {
                    entries.add("key:" + k + ":value:" + v);
                });
                for (String entry : entries) {
                    if (response.length() + entry.length() + "\n".length() <= MAX_STORE_LENGTH) {
                        response.append(entry).append("\n");
                    } else {
                        response = new StringBuilder("TRIMMED:").append(response.substring(0, MAX_STORE_LENGTH - "TRIMMED:".length()));
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
}
