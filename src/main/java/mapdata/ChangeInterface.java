package mapdata;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ChangeInterface extends Remote {

    public String changeData(String valuestring, String type) throws RemoteException;
}
