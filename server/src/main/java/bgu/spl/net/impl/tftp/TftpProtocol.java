package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
//import java.util.stream.Collectors;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    TftpServerUsers loggedUserList;
    Queue<byte[]> responseToUserQueue;
    Queue<byte[]> incomingDataQueue;
    String userName;
    String pathToDir;
    String fileNameInProcess;
    boolean needToBcast;
    int connectionId;
    Connections<byte[]> connections;
    byte[] response;
    boolean shouldTerminate;

    //CONSTRUCTOR
    public TftpProtocol(TftpServerUsers users){
        this.loggedUserList = users;
    }

    @Override 
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.needToBcast = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.pathToDir = "Files";        
    }
    
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 
    
    @Override
    //THE BIG MAMA
    public void process(byte[] message) {
        OpcodeOperations opcodeOp = new OpcodeOperations(message[1]);
        if (Opcode.UNDEFINED.equals(opcodeOp.opcode) || Opcode.BCAST.equals(opcodeOp.opcode)){
            generateError(4, "Illegal TFTP operation");
        }
        if (!(opcodeOp.opcode.equals(Opcode.LOGRQ) || loggedUserList.isUserLoggedIn(userName))){
            generateError(6, "User not logged in");
        } else {
            switch (opcodeOp.opcode) {
                case LOGRQ:
                    userLogin(message);
                    break;
                case DELRQ:
                    fileToDelete(message);
                    break;
                case RRQ:
                    processReadRequest(message);
                    break;
                case WRQ:
                    prepareToReadFromUser(message);
                    break;
                case DIRQ:
                    getDirectory();
                    break;
                case DATA:
                    getData(message);
                    break;
                case ACK:
                    processAck(message);
                    break;
                case ERROR:
                    processError(message);
                    break;
                case DISC:
                    disconnect();
                    return;
            }
        }
        responseToSpecificUser();
        brcastToUser(message);        
    }

    //1
    private String extractStringFromMsg(byte[] message){
        return new String(Arrays.copyOfRange(message,2, message.length));
    }
    //2
    private byte[] extractBytesFromMsg(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }
    //3
    private OpcodeOperations extractOpFromMessage(byte[] message) {
        return new OpcodeOperations(message[1]);
    }
    //4
    private void generateGeneralAck() {
        OpcodeOperations op = new OpcodeOperations(Opcode.ACK);
        response = op.getGeneralAck();
    }
    //5
    private byte[] convertIntToByte(int number) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((number >> 8) & 0xFF);
        bytes[1] = (byte) (number & 0xFF);
        return bytes;
    }
    
    //ACK
    private void processAck(byte[] message) {
        if (ackForPacket(message)){
            if (ackPacketSuccesses(message)){
                responseToUserQueue.remove(); //Packet was sent and received
                response = responseToUserQueue.peek();
            } else {
                throw new RuntimeException("The ACK packet that was received does not match the last packet that was send, received ACK for the packet number " + extractDataPacketNumber(message));
            }
        }
    }

    //CONNECTED ACK returns if is a non 0 ACK
    private boolean ackForPacket(byte[] message) {
        return extractAckPacketNumber(message) != 0;
    }

    //CONNECTED ACK returns ack block num 
    private int extractAckPacketNumber(byte[] message) {
        return ((message[2] & 0xFF) << 8) | (message[3] & 0xFF);
    }
    
    //CONNECTED ACK checks if packet num = block num
    private boolean ackPacketSuccesses(byte[] message) {
        return extractAckPacketNumber(message) == extractDataPacketNumber(responseToUserQueue.peek());
    }
    
    //CONNECTED ACK returns data block num 
    private int extractDataPacketNumber(byte[] dataPacket) {
        return ((dataPacket[4] & 0xFF) << 8) | (dataPacket[5] & 0xFF);
    }

    //MSG to one user
    private void responseToSpecificUser() {
        byte[] theResponse = encode(response);
        response = null;
        if(theResponse!=null)
            connections.send(connectionId, theResponse);
    }

    //BRCAST
    private void brcastToUser(byte[] message) {
        if(needToBcast)
        {
            byte[] fileNameInBytes = extractBytesFromMsg(fileNameInProcess);
            byte[] prefix = new byte[3];
            OpcodeOperations opToSend = new OpcodeOperations(Opcode.BCAST);
            System.arraycopy(opToSend.getInResponseFormat(), 0, prefix, 0, 2);
            OpcodeOperations opFromMessage = extractOpFromMessage(message);
            int i = opFromMessage.opcode.ordinal();
            prefix[2] = (i == 8) ? (byte) 0 : (byte) 1;
            byte[] toBroadcast = new byte[prefix.length + fileNameInBytes.length];
            System.arraycopy(prefix, 0, toBroadcast, 0, prefix.length);
            System.arraycopy(fileNameInBytes, 0, toBroadcast, prefix.length, fileNameInBytes.length);
            Set<Integer> activeConnections = loggedUserList.getLoggedInUsersId();
            toBroadcast = encode(toBroadcast);
            for (Integer connectedUser :
                    activeConnections) {
                connections.send(connectedUser, toBroadcast);
            }
            fileNameInProcess = "";
            needToBcast = false;
        }
    }
    
    //ERROR
    private void generateError(int errorCode, String errorMsg) {
        OpcodeOperations opcodeOperations = new OpcodeOperations(Opcode.ERROR);
        byte[] errorPrefix = opcodeOperations.getInResponseFormat((byte) errorCode);
        byte[] errorMessage = extractBytesFromMsg(errorMsg);
        response = new byte[errorMessage.length + errorPrefix.length];
        System.arraycopy(errorPrefix, 0, response, 0, errorPrefix.length);
        System.arraycopy(errorMessage, 0, response, errorPrefix.length, errorMessage.length);
    }
    
    //ERROR
    private void processError(byte[] message) {
        System.out.println(extractStringFromMsg(message)); //For human use
    }

    //LOGRQ
    private void userLogin(byte[] message){
        userName = extractStringFromMsg(message);
        if(loggedUserList.isUserLoggedIn(userName)){
            generateError(7, "User already logged in");
        }
        else{
            loggedUserList.logInUser(userName, connectionId);
            generateGeneralAck();
        }
    }
    
    //DISC
    private void disconnect(){
        generateGeneralAck();
        if(response != null) 
            connections.send(connectionId, encode(response));
        response = null;
        loggedUserList.logOutUser(connectionId);
        connections.disconnect(connectionId);
        shouldTerminate = true;        
    }

    //ENCODE
    public byte[] encode(byte[] message) {
        if ((message != null) && (hasToAddZeroByte(message))){
            byte[] zero = {(byte) 0};
            byte[] modified = new byte[message.length + zero.length];
            System.arraycopy(message, 0, modified, 0, message.length);
            System.arraycopy(zero, 0, modified, message.length, zero.length);
            message = modified;
        }
        return message;
    }

    //CONNECTED ENCODE
    private boolean hasToAddZeroByte(byte[] message) {
        OpcodeOperations opcodeOperations = extractOpFromMessage(message);
        return opcodeOperations.shouldAddZero();
    }
    
    //DELRQ
    private void fileToDelete(byte[] message){
        String fileToDelete = extractStringFromMsg(message);
        if(!fileWithThisNameExist(fileToDelete))
            generateError(1, "File not found");
        else{
            File file = getTheFile(fileToDelete);
            if(file.delete()){
                needToBcast = true;
                fileNameInProcess = fileToDelete;
                generateGeneralAck();
            }
            else
                generateError(2, "Access violation – File cannot be written, read or deleted.");
        }
    }

    //CONNECTED DELRQ
    private boolean fileWithThisNameExist(String fileName) {
        File file = getTheFile(fileName);
        return file.exists();
    }

    //CONNECTED DELRQ : creates file in directory
    private File getTheFile(String fileName) {
        return new File(pathToDir + File.separator + fileName);        
    }
    
    //DATA
    private void getData (byte[] data){
        incomingDataQueue.add(data);
        generateAckReceived(data);
        if(data.length != 518){ //last packet size
            completeIncomingFile();
            incomingDataQueue = null;
        }        
    } 
    
    //CONNECTED DATA : ack for confirming data packet numbers 
    private void generateAckReceived(byte[] message) {
        OpcodeOperations opcodeOperationsResponse = new OpcodeOperations(Opcode.ACK);
        response = new byte[4];
        System.arraycopy(opcodeOperationsResponse.getInResponseFormat(), 0, response, 0, 2);
        byte[] packetNumber = new byte[]{message[4], message[5]};
        System.arraycopy(packetNumber, 0, response,2, packetNumber.length);
    } 
    
    //CONNECTED DATA : send the complete file
    private void completeIncomingFile() {
        byte[] readyToWrite = generateBytesFromData();
        try {
            File file = getTheFile(fileNameInProcess);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(readyToWrite);
            fileOutputStream.close();
            needToBcast = true; //someone wrote something to the server
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //CONNECTED DATA : creates a whole file from the data packets
    private byte[] generateBytesFromData() {
        byte[] inProcess = incomingDataQueue.remove();
        byte[] pureData = Arrays.copyOfRange(inProcess, 6, inProcess.length);
        while (!incomingDataQueue.isEmpty()){
            inProcess = incomingDataQueue.remove();
            pureData = mergeBytes(pureData, Arrays.copyOfRange(inProcess, 6, inProcess.length));
        }
        return pureData;
    }

    //CONNECTED DATA
    private byte[] mergeBytes(byte[] pureData, byte[] dataToMerge) {
        byte[] appended = new byte[pureData.length + dataToMerge.length];
        System.arraycopy(pureData, 0, appended, 0, pureData.length);
        System.arraycopy(dataToMerge, 0, appended, pureData.length, dataToMerge.length);
        return appended;
    }

    //RRQ
    private void processReadRequest(byte[] message) {
        String fileToRead = extractStringFromMsg(message);
        if(!fileWithThisNameExist(fileToRead))
            generateError(1, "File not found");
        byte[] fileData = getDataOfFile(fileToRead);
        createDataPackets(fileData);
    }
    
    //CONNECTED RRQ  
    private byte[] getDataOfFile(String name) {
        File file = getTheFile(name);
        byte[] data = new byte[(int) file.length()];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(data);
            fileInputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return data;
    }

    //CONNECTED RRQ 
    private void createDataPackets(byte[] data) {
        generateGeneralAck();
        connections.send(connectionId, response);
        response = new byte[]{};
        int numberOfPackets;
        numberOfPackets = (data.length / 512) + 1;
        responseToUserQueue = new LinkedList<>();
        for (int i = 1; i <= numberOfPackets; i++){
            int sizeOfData = Math.min(512, data.length - ((i -1) * 512));
            byte[] dataPacket = new byte[6 + sizeOfData];
            byte[] dataPrefix = generateDataPrefix(sizeOfData, i);
            System.arraycopy(dataPrefix, 0, dataPacket, 0, dataPrefix.length);
            if (sizeOfData != 0){ //FOR 1 PACKET
                System.arraycopy(data, (i - 1) * 512, dataPacket, 6, sizeOfData);
            }
            responseToUserQueue.add(dataPacket);
        }
        response = responseToUserQueue.peek(); //first packet ready
    }

    //RRQ : data creates 6 digit data prefix
    private byte[] generateDataPrefix(int sizeOfData, int packetNum) {
        byte[] prefix = new byte[6];
        OpcodeOperations operations = new OpcodeOperations("DATA");
        System.arraycopy(operations.getInResponseFormat(), 0, prefix, 0, operations.getInResponseFormat().length);
        System.arraycopy(convertIntToByte(sizeOfData), 0, prefix, 2, 2);
        System.arraycopy(convertIntToByte(packetNum), 0, prefix, 4, 2);
        return prefix;

    }
    

    //WRQ
    private void prepareToReadFromUser(byte[] message) {
        fileNameInProcess = extractStringFromMsg(message);
        if (fileWithThisNameExist(fileNameInProcess)){
            generateError(5, "File already exists");
        } 
        else {
            incomingDataQueue = new LinkedList<>();
            generateGeneralAck();
        }
    }

    //DIRQ  
    private void getDirectory() { 
        List<String> listOfFiles = new LinkedList<>();
        String directoryPath = pathToDir;            
        File directory = new File(directoryPath);             
        // Using listFiles method we get all the files of a directory        
        File[] files = directory.listFiles();  
        if (files != null) { 
            for (File file : files) { 
                listOfFiles.add(file.getName()); 
            } 
        }       
        generateDirFromStringToByte(listOfFiles);                    
    }
    
    //CONNECTED DIRQ
    private byte[] generateDirFromStringToByte(List<String> listOfFiles) {
        List<byte[]> listOfFilesAsByte = new LinkedList<>();        
        int sizeOfDir = 0;
        int pointerForAddingFiles = 0;
        if(listOfFiles == null) 
            System.out.println("The directory is empty or didn't convert to byte");
        for(String filename : listOfFiles){
            byte[] fileByteName = extractBytesFromMsg(filename);
            sizeOfDir += fileByteName.length;
            listOfFilesAsByte.add(fileByteName);
        }
        sizeOfDir += listOfFilesAsByte.size() - 1; //make gap between each file with 0 byte
        byte[] dirData = new byte[sizeOfDir];
        for(byte[] byteFileName : listOfFilesAsByte){
            System.arraycopy(byteFileName, 0, dirData, pointerForAddingFiles, byteFileName.length);
            pointerForAddingFiles += byteFileName.length;
            dirData[pointerForAddingFiles] = (byte) 0;
            pointerForAddingFiles++;
        }
        return dirData;
    }
}