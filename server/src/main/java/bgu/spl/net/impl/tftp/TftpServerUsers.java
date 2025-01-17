package bgu.spl.net.impl.tftp;

import java.util.HashMap;
import java.util.Set;

public class TftpServerUsers {
    private HashMap<Integer, String> loggedInUsers;


    public TftpServerUsers(){
        loggedInUsers = new HashMap<>();
    }

    public boolean isUserLoggedIn(String name) {
        return loggedInUsers.containsValue(name);
    }

    public synchronized void logInUser(String name, int id) {
        loggedInUsers.put(id,name);

    }

    public synchronized void logOutUser(int id) {
        loggedInUsers.remove(id);
    }

    public synchronized Set<Integer> getLoggedInUsersId(){
        return loggedInUsers.keySet();
    }
}
