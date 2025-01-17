package bgu.spl.net.srv;

import java.util.HashMap;

public class ConnectionsImpl<T> implements Connections<T>{
    private HashMap<Integer, ConnectionHandler<T>> connectionsMap = new HashMap<>();

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler){
        if (connectionsMap.containsKey(connectionId))
            return;
        connectionsMap.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> handler = connectionsMap.get(connectionId);
        if (handler == null) 
            return false;
        handler.send(msg);
        return true;
    }

    @Override
    public void disconnect(int connectionId){
        connectionsMap.remove(connectionId);
    }
}
