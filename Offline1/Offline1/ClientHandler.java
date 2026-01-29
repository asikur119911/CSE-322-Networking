package Offline1;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class ClientHandler extends Thread {
    private Socket socket;
    private String username;
    private File userDir;
    private File logFile;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            String requestedUser = (String) in.readObject();
            if (!Server.login(requestedUser)) {
                out.writeObject("ERROR: User already online");
                out.flush();
                return;
            }
            this.username = requestedUser;
            out.writeObject("SUCCESS");
            out.flush();

            this.userDir = new File(Server.BASE_DIRECTORY, username);
            if (!this.userDir.exists()) this.userDir.mkdirs();
            this.logFile = new File(userDir, "log.txt");

            while (true) {
                String command = (String) in.readObject();
                switch (command) {
                    case "LIST_USERS":
                        handleListUsers();
                        break;
                    case "LIST_MY_FILES":
                        handleListFiles(false);
                        break;
                    case "LIST_PUB_FILES":
                        handleListFiles(true);
                        break;
                    case "DOWNLOAD":
                        handleDownload();
                        break;
                    case "REQUEST_FILE":
                        handleFileRequest();
                        break;
                    case "READ_MSGS":
                        handleReadMessages();
                        break;
                    case "UPLOAD":
                        handleUpload();
                        break;
                    case "HISTORY":
                        handleViewHistory();
                        break;
                    case "EXIT":
                        return;
                }
            }
        } catch (Exception e) {
            // Connection dropped or error occurred
        } finally {
            if (username != null) Server.logout(username);
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void handleListUsers() throws IOException {
        String message = "--- User List ---\n";

        for (String user : Server.allUsers) {
            String status;
            if (Server.onlineUsers.contains(user)) {
                status = "[Online]";
            } else {
                status = "[Offline]";
            }
            message = message + user + " " + status + "\n";
        }
        out.writeObject(message);
        out.flush();
        System.out.println("User " + username + " requested to see list of users");
    }

    private void handleListFiles(boolean publicOnly) throws IOException {
        String message = "";
        boolean found = false;

        for (FileInfo info : Server.fileDatabase.values()) {

            if (publicOnly) {
                if (info.isPublic && !info.owner.equals(username)) {
                    message = message + "ID: " + info.fileID + " | " + info.fileName + " (Owner: " + info.owner + ")\n";
                    found = true;
                }
            } else {
                if (info.owner.equals(username)) {
                    String privacyTag;
                    if (info.isPublic) {
                        privacyTag = "PUB";
                    } else {
                        privacyTag = "PVT";
                    }

                    message = message + "ID: " + info.fileID + " | " + info.fileName + " [" + privacyTag + "]\n";
                    found = true;
                }
            }
        }
        if (found == false) {
            message = "No files found.";
        }
        out.writeObject(message);
        out.flush();
    }

    private void handleDownload() throws IOException, ClassNotFoundException {
        int fileID = in.readInt();
        FileInfo info = Server.fileDatabase.get(fileID);

        if (info == null || (!info.isPublic && !info.owner.equals(username))) {
            out.writeObject("ERROR");
            if (info == null) {
                out.writeObject("Access Denied Or File Not found.");
            } else {
                out.writeObject("File Not Found.");
            }
            out.flush();
            logAction(info != null ? info.fileName : "Unknown", "Download", "Failed");
            return;
        }

        File fileToSend = new File(Server.BASE_DIRECTORY + File.separator + info.owner, info.fileName);
        if (!fileToSend.exists()) {
            out.writeObject("ERROR");
            out.writeObject("Physical file missing on server.");
            out.flush();
            return;
        }

        out.writeObject("OK"); //1
        out.writeObject(info.fileName);//2
        out.writeLong(fileToSend.length());//3
        out.flush();

        FileInputStream fis = new FileInputStream(fileToSend);
        byte[] buffer = new byte[Server.MAX_CHUNK_SIZE];
        int bytesRead;

        while (true) {
            bytesRead = fis.read(buffer);  //read chunk by chunk
            if (bytesRead == -1) {
                break; // check end
            }

            byte[] dataToSend;

            if (bytesRead == buffer.length) {
                dataToSend = buffer;
            } else {
                dataToSend = Arrays.copyOf(buffer, bytesRead);
            }
            out.writeObject(dataToSend);
            out.flush();
            out.reset();
        }

        fis.close();

        out.writeObject("COMPLETED"); //4
        out.flush();
        out.reset();
        System.out.println("User: " + username + " downloaded the file " + fileToSend.getName());
        logAction(info.fileName, "Download", "Success");
    }

    private void handleFileRequest() throws IOException, ClassNotFoundException {
        String description = (String) in.readObject();
        String recipient = (String) in.readObject();
        int reqID = Server.generateRequestID();
        Server.requestDatabase.put(reqID, new RequestInfo(reqID, description, username));
        Server.addMessage(username, recipient, "Request ID " + reqID + " from " + username + ": " + description);
        out.writeObject(reqID);
        out.flush();
    }

    private void handleReadMessages() throws IOException {
        List<String> box = Server.userMailbox.get(username); //get all the msg as a list of string
        String message = "";

        synchronized (box) { //synchornised so that no new msg come while we read

            if (box.isEmpty()) {
                message = "No new messages.";
            } else {
                for (String m : box) {
                    message = message + m + "\n";
                }
                box.clear(); //all msg read so no unread msg
            }
        }
        out.writeObject(message);
        out.flush();
    }

    private void handleUpload() {
        long reservedSize = 0;
        String fileName = "Unknown";
        try {
            fileName = (String) in.readObject();  //1
            long fileSize = in.readLong();//2
            String privacy = (String) in.readObject();//3
            int reqID = in.readInt();//4

            if (!Server.requestUploadSpace(fileSize)) {
                out.writeObject("REJECTED");
                out.flush();
                return;
            }
            reservedSize = fileSize;

            int chunkRange = Server.MAX_CHUNK_SIZE - Server.MIN_CHUNK_SIZE;
            int chunkSize = (int) (Math.random() * chunkRange) + Server.MIN_CHUNK_SIZE;
            int fileID = (int) (System.currentTimeMillis() % 100000);

            out.writeObject("APPROVED");
            out.writeInt(chunkSize);
            out.writeInt(fileID);
            out.flush();

            File fileToSave = new File(userDir, fileName);
            FileOutputStream fos = new FileOutputStream(fileToSave);
            long totalBytesReceived = 0;

            while (true) {
                Object obj = in.readObject();
                if (obj instanceof String && ((String) obj).equals("COMPLETED")) break;
                if (obj instanceof byte[]) {
                    byte[] chunkData = (byte[]) obj;
                    fos.write(chunkData);
                    totalBytesReceived += chunkData.length;

                    out.writeObject("ACK");
                    out.flush();
                    out.reset();
                }
            }
            fos.close();

            if (totalBytesReceived == fileSize) {
                out.writeObject("SUCCESS");
                out.flush();

                boolean isPublic = privacy.equalsIgnoreCase("PUBLIC");
                if (reqID != 0 && Server.requestDatabase.containsKey(reqID)) {
                    isPublic = true;
                    RequestInfo reqInfo = Server.requestDatabase.get(reqID);
                    Server.addMessage(username, reqInfo.requester, "Your request (ID " + reqID + ") was fulfilled by " + username);
                }

                Server.fileDatabase.put(fileID, new FileInfo(fileID, fileName, username, isPublic));
                logAction(fileName, "Upload", "Success");
                System.out.println("User: " + username + " upload the file " + fileName);
            } else {
                out.writeObject("FAILED");
                out.flush();
                fileToSave.delete();
                logAction(fileName, "Upload", "Failed");
            }

        } catch (Exception e) {
            if (fileName != null) {
                File f = new File(userDir, fileName);
                if (f.exists()) f.delete(); //discard the file
            }
        } finally {
            if (reservedSize > 0) Server.releaseUploadSpace(reservedSize);
        }
    }

    private void handleViewHistory() throws IOException {
        String history = "";

        if (logFile.exists()) {
            Scanner sc = new Scanner(logFile);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                history = history + line + "\n";
            }
            sc.close();

        } else {
            history = "No history available.";
        }
        out.writeObject(history);
        out.flush();
        System.out.println("User: " + username + " viewed his history.");
    }

    private void logAction(String fileName, String action, String status) {
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            pw.println(time + " | File: " + fileName + " | Action: " + action + " | Status: " + status);
        } catch (IOException e) {
        }
    }
}