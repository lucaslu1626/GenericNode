package mapdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeImplementation implements ChangeInterface {
    private final ConcurrentHashMap<String, Lock> keyLocks = new ConcurrentHashMap<>();
    private final static int MAX_STORE_LENGTH = 65000;

    public ChangeImplementation() {
        super();
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
                }
                break;
            case "get":
                response.append("get key=").append(key).append(" get val=").append(getMap(type).get(key));
                break;
            case "del":
                getMap(type).remove(key);
                response.append("delete key=").append(key);
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
