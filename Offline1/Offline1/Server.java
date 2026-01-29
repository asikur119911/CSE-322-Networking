package Offline1;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    public static final long MAX_BUFFER_SIZE = 50 * 1024 * 1024; // 50 MB
    public static final int MIN_CHUNK_SIZE = 10 * 1024;  // 10 KB
    public static final int MAX_CHUNK_SIZE = 64 * 1024;  // 64 KB

    public static final String BASE_DIRECTORY = "Server/";

    // -----------Global variables  ---
    public static long currentBufferLoad = 0;
    public static List<String> allUsers = new ArrayList<>();
    public static Set<String> onlineUsers = ConcurrentHashMap.newKeySet();
    public static Map<Integer, FileInfo> fileDatabase = new ConcurrentHashMap<>();
    public static Map<Integer, RequestInfo> requestDatabase = new ConcurrentHashMap<>();
    public static Map<String, List<String>> userMailbox = new ConcurrentHashMap<>();
    public static Integer portNumber = 6666;

    public static void main(String[] args) throws IOException {
        ServerSocket welcomeSocket = new ServerSocket(portNumber);
        new File(BASE_DIRECTORY).mkdirs();
        System.out.println("Buffer Size: " + MAX_BUFFER_SIZE / (1024 * 1024) + "MB");
        System.out.println("Minimum Chunk Size: " + MIN_CHUNK_SIZE / 1024 + "KB");
        System.out.println("Maximum Chunk Size: " + MAX_CHUNK_SIZE / 1024 + "KB");
        System.out.println("Server listening at port: " + portNumber);

        while (true) {
            Socket socket = welcomeSocket.accept();
            // Start the separate ClientHandler thread
            new ClientHandler(socket).start();
        }
    }

    // --- Helper functions used by ClientHandler ---

    public static synchronized boolean login(String username) {
        if (onlineUsers.contains(username)) return false;
        onlineUsers.add(username);
        if (!allUsers.contains(username)) allUsers.add(username);
        userMailbox.putIfAbsent(username, new ArrayList<>());
        System.out.println("User: " + username + " connected..");
        return true;
    }
    
    public static synchronized void logout(String username) {
        onlineUsers.remove(username);
        System.out.println("User " + username + " disconnected from Server");
    }

    public static synchronized boolean requestUploadSpace(long size) {
        if (currentBufferLoad + size <= MAX_BUFFER_SIZE) {
            currentBufferLoad += size;
            return true;
        }
        return false;
    }

    public static synchronized void releaseUploadSpace(long size) {
        currentBufferLoad -= size;
        if (currentBufferLoad < 0) currentBufferLoad = 0;
    }

        public static void addMessage(String sender, String recipient, String msg) {
            
            if (recipient.equalsIgnoreCase("ALL")) {
                for (String user : allUsers) {
                    if (!user.equals(sender)) { 
                        userMailbox.computeIfAbsent(user, k -> new ArrayList<>()).add(msg);
                    }
                }
            } else {
                List<String> box = userMailbox.get(recipient);
                if (box != null) {
                    synchronized (box) {
                        box.add(msg);
                    }
                }
            }
        }
    public static synchronized int generateRequestID() {
        return (int) (System.currentTimeMillis() % 100000);
    }
}

// --- Helper Classes 

class FileInfo implements Serializable {
    int fileID;
    String fileName;
    String owner;
    boolean isPublic;

    public FileInfo(int id, String n, String o, boolean p) {
        fileID = id;
        fileName = n;
        owner = o;
        isPublic = p;
    }
}

class RequestInfo {
    int reqID;
    String description;
    String requester;

    public RequestInfo(int id, String d, String r) {
        reqID = id;
        description = d;
        requester = r;
    }
}