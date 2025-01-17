package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {
        TftpServerUsers userList = new TftpServerUsers();
        Server.threadPerClient(Integer.parseInt(args[0]),()-> new TftpProtocol(userList), TftpEncoderDecoder::new).serve();
    }
}
