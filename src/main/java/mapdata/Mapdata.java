package mapdata;

import java.util.concurrent.ConcurrentHashMap;

public class Mapdata {
    static ConcurrentHashMap<String,String> tcpmap = new ConcurrentHashMap();

    static ConcurrentHashMap<String,String> udpmap = new ConcurrentHashMap();
}

