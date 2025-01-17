package bgu.spl.net.impl.tftp;

import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    
    private final int size  = 1 << 9;  //start with 512 bytes
    private byte[] bytes = new byte[size];
    private int len = 0;
    private OpcodeOperations opcodeOp;    
    
    @Override
    public byte[] decodeNextByte(byte nextByte) {
        //after 2 bytes we will not enter the initial "if"       
        if(len <= 1){
            if(len == 1){
                opcodeOp = new OpcodeOperations(nextByte);
                if(opcodeOp.opcode.equals(Opcode.DISC) || opcodeOp.opcode.equals(Opcode.DIRQ)){
                    pushByte(nextByte);
                    return popBytes();
                }
            }
        }
        else{
            if(opcodeOp.hasSpecificMsgSize()){
                int expectedLength = opcodeOp.getExpectedSize();
                if(len == expectedLength - 1){
                    pushByte(nextByte);
                    return popBytes();
                }                
            }
            else if(opcodeOp.opcode.equals(Opcode.DATA)){
                if(len >= 4){
                    int expectedLength = getTotalPacketLength();
                    if (len == expectedLength - 1){
                        pushByte(nextByte);
                        return popBytes();
                    }
                }
            }
            else{
                if(nextByte == 0)
                    return popBytes();
            }            
        }

        pushByte(nextByte);
        return null;
    }

    private int getTotalPacketLength() {
        return ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF) + 6;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len++] = nextByte;
    }
    //We get a full message in results array, no matter the size of it.
    private byte[] popBytes() {
        byte[] result = Arrays.copyOfRange(bytes, 0, len);
        len = 0;
        opcodeOp = new OpcodeOperations(Opcode.UNDEFINED);
        bytes = new byte[size];
        return result;
    }
}