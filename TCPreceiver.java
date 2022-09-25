import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;


/*
FLAGS: 
    - ACK: 1
    - FIN: 2
    - SYN: 4
    - FIN + ACK: 3
    - SYN + ACK: 5
 */

public class TCPreceiver{

    int port;
    String filename;
    int mtu;
    int sws;

    DatagramSocket recSock;

    FileOutputStream fileStream;
    
    boolean connected;
    boolean synRec;

    long lastTimeRec;
    int lastDataLenRec;

    int numPack;
    int numDisc;

    boolean finack = false;

    int nextByte;


    public TCPreceiver(int port, String filename, int mtu, int sws){
        this.port = port;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;

        connected = false;
        synRec = false;

        lastDataLenRec = 0;
        lastTimeRec = 0;

        numPack = 0;
        numDisc = 0;

        int nextByte = 0;
        try{
            recSock = new DatagramSocket(port);
            receiver();
        } catch (Exception e){
            e.printStackTrace();
            System.exit(0);
        }

    }

    public void receiver() throws IOException, FileNotFoundException,SocketException{
        //int count = 0;
        while(true){
            byte[] dataArray =  new byte[mtu+24];
            DatagramPacket incPacket = new DatagramPacket(dataArray,mtu+24);
            recSock.receive(incPacket);
            numPack++;

            int destPort = incPacket.getPort();
            InetAddress destAddr = incPacket.getAddress();

            ByteBuffer dataBuff = ByteBuffer.wrap(incPacket.getData());
            byte[] bufferArr = dataBuff.array();
            int seqNo = dataBuff.getInt();
            int ackRec = dataBuff.getInt();
            long timePack = dataBuff.getLong();
            int lengthandflags = dataBuff.getInt();
            
            int mask = (1 << 3) - 1;
            int flag = lengthandflags & mask;
            int dataLen = lengthandflags >> 3;
            byte[] dataArr = Arrays.copyOfRange(bufferArr,24,24+dataLen);


            if(!connected){
                if(flag == 4){
                    System.out.format("rcv %d S - - - 0 0 0\n",System.nanoTime());
                    synRec = true;
                    File file = new File(filename);
					fileStream = new FileOutputStream(file);
                    nextByte = 1;
                    byte[] packetData = createPacket(0,1,5,null);
                    DatagramPacket sendingPacket = new DatagramPacket(packetData, packetData.length, destAddr, destPort);
                    System.out.format("snd %d S A - - 0 0 1\n",System.nanoTime());
                    recSock.send(sendingPacket);
                }

                if(flag == 1 && synRec && ackRec == 1){
                    System.out.format("rcv %d - A - - 1 0 1\n",timePack);
                    connected = true;
                }
            }
            else{
                if(flag == 1 && !finack){
                    System.out.format("rcv %d - A - D %d %d %d\n",timePack,seqNo,dataLen,ackRec);
                    //System.out.println(seqNo + "  |  " + nextByte);
                    if(seqNo == nextByte){
                        //System.out.println("BYTE Expected");
                         lastDataLenRec = dataLen;
                        lastTimeRec = timePack;
                        nextByte += dataLen;
                        fileStream.write(dataArr);
                        byte[] packetData = createACKPacket(1,nextByte,1,timePack,dataLen);
                        System.out.format("snd %d - A - - %d %d %d\n",timePack,1,dataLen,nextByte);
                        DatagramPacket sendingPacket = new DatagramPacket(packetData, packetData.length, destAddr, destPort);
                        recSock.send(sendingPacket);
                    }

                    else{
                        numDisc++;
                        byte[] packetData = createACKPacket(1,nextByte,1,lastTimeRec,lastDataLenRec);
                        System.out.format("snd %d - A - - %d %d %d\n",lastTimeRec,1,lastDataLenRec,nextByte);
                        DatagramPacket sendingPacket = new DatagramPacket(packetData, packetData.length, destAddr, destPort);
                        recSock.send(sendingPacket);
                    }
                }
                else if(flag == 1 && finack){
                    printStats();
                    System.exit(1);
                }
                if(flag == 2){
                    System.out.format("rcv %d - - F - %d 0 1\n",timePack,nextByte);
                    byte[] packetData = createPacket(1,seqNo+1,3,null);
                    DatagramPacket sendingPacket = new DatagramPacket(packetData, packetData.length, destAddr, destPort);
                    finack = true;
                    System.out.format("snd %d - A F - 1 0 %d\n",timePack,nextByte+1);
                    recSock.send(sendingPacket);

                }
            }
        }
    }

    public void printStats(){
        System.out.println("\nAmount of Data Received: " + nextByte);
        System.out.println("Number of Packets Received: " + numPack);
        System.out.println("Number of Out-of-Sequence Packets Discarded: " + numDisc);
        System.out.println("Number of Packets Discarded Due to Incorrect Checksum: 0");
        System.out.println("Number of Retransmissions: 0");
        System.out.println("Number of Duplicate Acknowledgements: 0\n");
    }

    public byte[] createACKPacket(int seqNum,int ack, int flag, long timestamp, int dataLen){
        
        ByteBuffer packet = ByteBuffer.wrap(new byte[24]);
        packet.putInt(seqNum);
        packet.putInt(ack);
        packet.putLong(timestamp);

        int dataLenAndFlags = dataLen << 3;
        dataLenAndFlags = dataLenAndFlags | flag;
        packet.putInt(dataLenAndFlags);

        int checksum = 0;
        packet.putInt(checksum);
        return packet.array();

    }

    public byte[] createPacket(int seqNum,int ack, int flag, byte[] data){
        
        if(data != null){
            int dataLen = data.length;

            ByteBuffer packet = ByteBuffer.wrap(new byte[24 + dataLen]);
            packet.putInt(seqNum);
            packet.putInt(ack);
            packet.putLong(System.nanoTime());

            int dataLenAndFlags = dataLen << 3;
            dataLenAndFlags = dataLenAndFlags | flag;
            packet.putInt(dataLenAndFlags);

            int checksum = 0;
            packet.putInt(checksum);

            packet.put(data);
            return packet.array();
        }
        else{
            ByteBuffer packet = ByteBuffer.wrap(new byte[24]);
            packet.putInt(seqNum);
            packet.putInt(ack);
            packet.putLong(System.nanoTime());

            int dataLenAndFlags = 0 << 3;
            dataLenAndFlags = dataLenAndFlags | flag;
            packet.putInt(dataLenAndFlags);

            int checksum = 0;
            packet.putInt(checksum);
            return packet.array();

        }
       
    }

    public void printPacket(byte[] packet){

		int seqNum =  ByteBuffer.wrap(Arrays.copyOfRange(packet, 0, 4)).getInt();
        int ack =  ByteBuffer.wrap(Arrays.copyOfRange(packet, 4, 8)).getInt();
        long sysTime =  ByteBuffer.wrap(Arrays.copyOfRange(packet, 8, 16)).getLong();
        int dataLenAndFlags = ByteBuffer.wrap(Arrays.copyOfRange(packet, 16, 20)).getInt();
        int checksum = ByteBuffer.wrap(Arrays.copyOfRange(packet, 20, 24)).getInt();
        int mask = (1 << 3) - 1;
        int flag = dataLenAndFlags & mask;
        int dataLen = dataLenAndFlags >> 3;

        System.out.println("-----------------------------");
        System.out.println("SEQ NUM : " + seqNum);
        System.out.println("ACK : " + ack);
        System.out.println("SYS TIME : " + sysTime);
        System.out.println("FLAGS : " + flag);
        System.out.println("DATA LEN : " + dataLen);
        System.out.println("CHECKSUM : " + checksum);
        System.out.println("-----------------------------");


    }
}