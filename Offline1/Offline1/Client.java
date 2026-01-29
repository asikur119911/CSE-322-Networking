package Offline1;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

public class Client {
    //-----Global variabes-----
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static Scanner sc = new Scanner(System.in);
    private static String username;
    
    private static String clientBaseDirectory = "Client/"; 
    
    private static Integer portNumber = 6666;

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", portNumber);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            System.out.print("Enter Username: ");
            username = sc.nextLine();
            out.writeObject(username);
            out.flush();

            String response = (String) in.readObject();
            if (!"SUCCESS".equals(response)) {
                System.out.println("Login Failed: " + response);
                return;
            }
            System.out.println("Connected to Server at port: " + portNumber);

            while (true) {
                clientChoices();
                String choice = sc.nextLine().trim().toLowerCase();

                switch (choice) {
                    case "a": requestUserList(); break;
                    case "b": listFilesAndDownload("LIST_MY_FILES"); break;
                    case "c": listFilesAndDownload("LIST_PUB_FILES"); break;
                    case "d": makeFileRequest(); break;
                    case "e": viewUnreadMessages(); break;
                    case "f": uploadFileFlow(); break;
                    case "g": viewHistory(); break;
                    case "q":
                        out.writeObject("EXIT");
                        out.flush();
                        System.exit(0);
                    default: System.out.println("Invalid option.");
                }
            }
        } catch (Exception e) {
            System.out.println("\n*** FATAL PROGRAM ERROR ***");
            System.out.println("Details: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(); 
            System.out.println("The program is terminating.");
        }
    }

    private static void clientChoices() {
        System.out.println("\n--- Client Choices ---");
        System.out.println("Just type the choice: a/b/c...");
        System.out.println("a. List Users");
        System.out.println("b. Show My Files & (Download)");
        System.out.println("c. Show Public Files & (Download)");
        System.out.println("d. Request File");
        System.out.println("e. Unread Messages");
        System.out.println("f. Upload File");
        System.out.println("g. History");
        System.out.println("q. To exit");
        System.out.print("Select: ");
    }

    private static void requestUserList() throws IOException, ClassNotFoundException {
        out.writeObject("LIST_USERS");
        out.flush();
        System.out.println((String) in.readObject());
    }

    private static void listFilesAndDownload(String command) throws IOException, ClassNotFoundException {
        out.writeObject(command);
        out.flush();
        String list = (String) in.readObject();
        System.out.println(list);

        if (list.contains("ID:")) { //ID nai mane kono file nai, no download option needed
            System.out.print("Enter File ID to download (0 to cancel): ");
            String input = sc.nextLine();
            try {
                int id = Integer.parseInt(input);
                if (id != 0) downloadFile(id);
            } catch (NumberFormatException e) { System.out.println("Invalid ID"); }
        }
    }

    private static void downloadFile(int fileID) throws IOException, ClassNotFoundException {
        out.writeObject("DOWNLOAD");
        out.writeInt(fileID);
        out.flush();

        String status = (String) in.readObject();  //1. status error kina
        if ("ERROR".equals(status)) {   // !OK
            System.out.println("Server Error: " + in.readObject());
            return;
        }

        String fileName = (String) in.readObject(); //2 file name
        long fileSize = in.readLong();  //3 file size
        System.out.println("Downloading: " + fileName + " (" + fileSize + " bytes)...");

        File downloadDirectory = new File(clientBaseDirectory + username);
        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs();
        }
        File file = new File(downloadDirectory, fileName); 

        FileOutputStream fos = new FileOutputStream(file);

        while (true) {
            Object obj = in.readObject();
            if (obj instanceof String && ((String)obj).equals("COMPLETED")) break; //4
            if (obj instanceof byte[]) fos.write((byte[])obj);
        }
        
        fos.close();
        System.out.println("Download Status: COMPLETED");
    }

    private static void makeFileRequest() throws IOException, ClassNotFoundException {
        System.out.print("Description: ");
        String desc = sc.nextLine();
        System.out.print("Recipient (Username or 'ALL'): ");
        String recipient = sc.nextLine();
        out.writeObject("REQUEST_FILE");
        out.writeObject(desc);
        out.writeObject(recipient);
        out.flush();
        System.out.println("Request broadcasted to "+recipient+". Request ID: " + in.readObject());
    }

    private static void viewUnreadMessages() throws IOException, ClassNotFoundException {
        out.writeObject("READ_MSGS");
        out.flush();
        System.out.println((String) in.readObject());
    }

   
    private static void uploadFileFlow() throws IOException, ClassNotFoundException {
        System.out.print("File Path: ");
        String path = sc.nextLine();
        File file = new File(path);
        
        if (!file.exists()) {
            System.out.println("File not found at: " + path);
            return;
        }

        int reqID = 0;
        String privacy = "PRIVATE";

        System.out.print("Enter Request ID (Press Enter if none): ");
        try {
            String input = sc.nextLine();
            reqID = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            reqID = 0; // Default to normal upload
        }

        if (reqID > 0) {
            privacy = "PUBLIC"; 
            System.out.println("Responding to Request #" + reqID + ". Privacy set to PUBLIC.");
        } else {
            System.out.print("Privacy (public/private): ");
            privacy = sc.nextLine().toUpperCase();
        }
        if(!privacy.equalsIgnoreCase("private") && !privacy.equalsIgnoreCase("public")){
            System.out.println("Enter the valid choice");
            return;
        }
        out.writeObject("UPLOAD");
        out.writeObject(file.getName());//1
        out.writeLong(file.length());//2
        out.writeObject(privacy);//3
        out.writeInt(reqID);//4
        out.flush(); 

        String response = (String) in.readObject();
        if ("REJECTED".equals(response)) {
            System.out.println("Upload Rejected: Buffer Full.");
            return;
        }

        int chunkSize = in.readInt();
        int fileID = in.readInt();
        System.out.println("Starting Upload (File ID: " + fileID + ", ChunkSize: " + chunkSize/1024 + "KB)");

        FileInputStream fis = new FileInputStream(file);

        byte[] buffer = new byte[chunkSize];
        int bytesRead;
        long totalFileSize = file.length();

        long totalBytesSent = 0;
        int nextProgressTarget = 20; // Print progress for 20s multiple

        while (true) {
            bytesRead = fis.read(buffer); 
            if (bytesRead == -1) {   // check end
                break; 
            }
            totalBytesSent += bytesRead;
            int currentPercent = (int) ((totalBytesSent * 100) / totalFileSize);

            // Check the progess  target (20, 40, 60, etc.)
            while (currentPercent >= nextProgressTarget && nextProgressTarget <= 100) {
                System.out.println(nextProgressTarget + "% Upload done...");
                nextProgressTarget += 20;
            }

            byte[] dataToSend;
            if (bytesRead == chunkSize) {
                dataToSend = buffer;
            } else {
                dataToSend = Arrays.copyOf(buffer, bytesRead);
            }

            out.writeObject(dataToSend);
            out.flush();
            out.reset(); 

            String ack = (String) in.readObject();
            if (!"ACK".equals(ack)) {
                System.out.println("Upload failed");
                break;
            }
        }
        fis.close();

        out.writeObject("COMPLETED");
        out.flush();
        out.reset();

        System.out.println("File Upload " + in.readObject());
    }

    private static void viewHistory() throws IOException, ClassNotFoundException {
        out.writeObject("HISTORY");
        out.flush();
        System.out.println((String) in.readObject());
    }
}