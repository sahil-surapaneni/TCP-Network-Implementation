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
public class TCPsender{

    int inPort;
    InetAddress ip;
    int outPort;
    String filename;
    int mtu;
    int sws;

    

    byte[] databytes;
    long timeout;

    boolean connected;
    boolean reachedEnd;

    int fSeqNum;
    int lAckRec;

    ConcurrentHashMap<Integer,PacketInfo> buffer;

    DatagramSocket inSock;
    DatagramSocket outSock;

    int numRetrans;
    int dupAcks;
    int packSent;
    int dataSent;

    Semaphore s = new Semaphore(1);

    public class PacketInfo{

        byte[] data;
        Timer timer;
        int numTrans;

        public PacketInfo(byte[] data, Timer timer, int numTrans){
            this.data = data;
            this.timer = timer;
            this.numTrans = numTrans;
        }

        public byte[] getData(){
            return data;
        }

        public Timer getTimer(){
            return timer;
        }

        public int getNumTrans(){
            return numTrans;
        }

        public void resetTimer(){
            timer = new Timer();
        }

        public void incrementNumTrans(){
            numTrans++;
        }

    }


    public TCPsender(int inPort, String ips, int outPort, String filename, int mtu, int sws){
        this.inPort = inPort;
        this.outPort = outPort;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        this.connected = false;
        this.timeout = 500;

        this.fSeqNum = 0;
        this.lAckRec = 0;

        this.buffer = new ConcurrentHashMap<Integer,PacketInfo>();
    


        this.numRetrans = 0;
        this.dupAcks = 0;
        this.packSent = 0;
        this.dataSent = 0;

        try{
            this.ip = InetAddress.getByName(ips);
            File file = new File(filename);
            this.databytes = Files.readAllBytes(Paths.get(filename));

            outSock = new DatagramSocket(inPort);

            OutgoingThread outThread = new OutgoingThread();
            IncomingThread inThread = new IncomingThread();

            outThread.start();
            inThread.start();

            byte[] connectionData = createPacket(fSeqNum,0,4,null);
            buffer.put(fSeqNum,new PacketInfo(connectionData,new Timer(),0));
            buffer.get(fSeqNum).getTimer().schedule(new TimeoutHandler(fSeqNum),timeout);
            DatagramPacket connectionPacket = new DatagramPacket(connectionData, connectionData.length, ip, outPort);
            outSock.send(connectionPacket);
            packSent++;
            System.out.format("snd %d S - - - 0 0 0\n",System.nanoTime());



        } catch (Exception e){
            e.printStackTrace();
            System.exit(0);
        }
    }

    public void retransmit(int seqN){

        // if(seqN < lAckRec){
        //     System.out.println("SEQ NO: " + seqN);
        //     System.out.println("LAST ACK: " + lAckRec);
        // }

        if(!buffer.containsKey(seqN)){
            return;
        }

        int numRet = buffer.get(seqN).getNumTrans();
        byte[] data = buffer.get(seqN).getData();

        if(numRet >= 16){
            System.err.println("Max Retransmission Reached");
            System.exit(1);
        }
        else{

            if(data.length > 24){
                try{
                    PacketInfo newInf = new PacketInfo(data,new Timer(),numRet+1);
                    Iterator<Integer> it = buffer.keySet().iterator();
                    while(it.hasNext()){
                        int seqNoRem = it.next(); 
                        buffer.get(seqNoRem).getTimer().cancel();
                        it.remove();
                    }
                    buffer.put(seqN,newInf);
                    ByteBuffer packet = ByteBuffer.wrap(data);
                    buffer.get(seqN).getTimer().schedule(new TimeoutHandler(seqN),timeout);
                    fSeqNum = seqN;
                    long currTime = System.nanoTime();
                    packet.putLong(8,currTime);
                    DatagramPacket sendingPacket = new DatagramPacket(packet.array(), data.length, ip, outPort);
                    outSock.send(sendingPacket);
                    packSent++;
                    System.out.format("snd %d - A - D %d %d 1\n",currTime,seqN,data.length-24);

                } catch(Exception e){
                    // e.printStackTrace()
                    // System.exit(1);
                }
            }

            else{
                try{
                    Iterator<Integer> it = buffer.keySet().iterator();
                    while(it.hasNext()){
                        int seqNoRem = it.next(); 
                        if(seqNoRem < lAckRec){
                            buffer.get(seqNoRem).getTimer().cancel();
                            it.remove();
                        }
                    }

                    ByteBuffer packet = ByteBuffer.wrap(data);
                    buffer.put(seqN,new PacketInfo(data,new Timer(),numRet+1));
                    buffer.get(seqN).getTimer().schedule(new TimeoutHandler(seqN),timeout);
                    packet.putLong(8,System.nanoTime());
                    DatagramPacket sendingPacket = new DatagramPacket(packet.array(), data.length, ip, outPort);
                    outSock.send(sendingPacket);
                    packSent++;



                }catch(Exception e){
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            
        }
    
    }

    public class TimeoutHandler extends TimerTask{
        int seqN;

        public TimeoutHandler(int seqN){
            this.seqN = seqN;
        }
        public void run(){
            numRetrans++;
            retransmit(lAckRec);
        }
    }

    public class OutgoingThread extends Thread{
        public void run() {
            try{
                while(true){
                    System.out.format("");
                    // System.out.println("SEQUENCE NUM: " + fSeqNum);
                    // System.out.println("LAST ACK: " + lAckRec);
                    //System.out.format("test\n");
                    if(!connected){
                        continue;
                    }
                    else{
                        // System.out.println("SEQUENCE NUM: " + fSeqNum);
                        // System.out.println("LAST ACK: " + lAckRec);
                        
                        //System.out.println(buffer.keySet());
                        if(buffer.size() == 0){

                            if(reachedEnd && lAckRec >= databytes.length){
                                byte[] packetData = createPacket(fSeqNum,1,2,null);
                                ByteBuffer packet = ByteBuffer.wrap(packetData);
                                buffer.put(fSeqNum,new PacketInfo(packetData,new Timer(),0));
                                buffer.get(fSeqNum).getTimer().schedule(new TimeoutHandler(fSeqNum),timeout);
                                long currTime = System.nanoTime();
                                packet.putLong(8,currTime);
                                DatagramPacket sendingPacket = new DatagramPacket(packet.array(), packetData.length, ip, outPort);
                                outSock.send(sendingPacket);
                                packSent++;
                                System.out.format("snd %d - - F - %d 0 1\n",currTime,fSeqNum);
                                break;
                            }
                            
                            int tempSeq = fSeqNum;
                            for(int i = 0; i<sws; i++){
                                if(tempSeq + mtu >= databytes.length && tempSeq <= databytes.length){
                                    
                                    i++;
                                    byte[] dataSending = Arrays.copyOfRange(databytes, tempSeq-1, databytes.length);
                                    reachedEnd = true;
                                    byte[] packetData = createPacket(tempSeq,1,1,dataSending);
                                    ByteBuffer packet = ByteBuffer.wrap(packetData);
                                    int dataLenAndFlags = packet.getInt(16);
                                    int mask = (1 << 3) - 1;
                                    int flag = dataLenAndFlags & mask;
                                    int dataLen = dataLenAndFlags >> 3;
                                    packSent++;
                                    buffer.put(tempSeq,new PacketInfo(packetData,new Timer(),0));
                                    buffer.get(tempSeq).getTimer().schedule(new TimeoutHandler(tempSeq),timeout);
                                    //buffer.put(tempSeq,packetData);
                                    long currTime = System.nanoTime();
                                    packet.putLong(8,currTime);
                                    DatagramPacket sendingPacket = new DatagramPacket(packet.array(), packetData.length, ip, outPort);
                                    outSock.send(sendingPacket);
                                    System.out.format("snd %d - A - D %d %d 1\n",currTime,tempSeq,dataLen);
                                    tempSeq += dataSending.length;
                                    break;
                                }

                                byte[] dataSending = Arrays.copyOfRange(databytes, tempSeq-1, tempSeq-1+mtu);
                                byte[] packetData = createPacket(tempSeq,1,1,dataSending);
                                ByteBuffer packet = ByteBuffer.wrap(packetData);
                                int dataLenAndFlags = packet.getInt(16);
                                int mask = (1 << 3) - 1;
                                int flag = dataLenAndFlags & mask;
                                int dataLen = dataLenAndFlags >> 3;
                                packSent++;
                                buffer.put(tempSeq,new PacketInfo(packetData,new Timer(),0));
                                buffer.get(tempSeq).getTimer().schedule(new TimeoutHandler(tempSeq),timeout);
                                //buffer.put(tempSeq,packetData);
                                long currTime = System.nanoTime();
                                packet.putLong(8,currTime);
                                DatagramPacket sendingPacket = new DatagramPacket(packet.array(), packetData.length, ip, outPort);
                                outSock.send(sendingPacket);
                                System.out.format("snd %d - A - D %d %d 1\n",currTime,tempSeq,dataLen);
                                tempSeq = tempSeq + mtu;
                            }
                            fSeqNum = tempSeq;
                            // System.out.println("BUFFER SIZE: " + buffer.size());
                            // System.out.println("BUFFER : " + buffer.keySet());
                        }
                    }
                }
            } catch(Exception e){
                e.printStackTrace();
                System.exit(0);
            }

        }
    }

    public class IncomingThread extends Thread{
        public void run() {
            int dupCount = 0;
            try{
                while(true){
                    System.out.format("");
                    byte[] dataArr =  new byte[24];
                    DatagramPacket incPacket = new DatagramPacket(dataArr,24);
                    outSock.receive(incPacket);

                    ByteBuffer dataBuff = ByteBuffer.wrap(incPacket.getData());
                    int seqNo = dataBuff.getInt();
                    int ackRec = dataBuff.getInt();
                    long timePack = dataBuff.getLong();
                    int lengthandflags = dataBuff.getInt();
                    
                    int mask = (1 << 3) - 1;
                    int flag = lengthandflags & mask;
                    int dataLen = lengthandflags >> 3;

                    //System.out.println(buffer.keySet());
                    //printPacket(dataBuff.array());

                    if(!connected){
                        if(flag == 5){
                            
                            System.out.format("rcv %d S A - - 0 0 1\n",System.nanoTime());
                            lAckRec = ackRec;
                            connected = true;
                            fSeqNum = 1;
                            byte[] packetData = createPacket(fSeqNum,1,1,null);
                            ByteBuffer packet = ByteBuffer.wrap(packetData);
                            long currTime = System.nanoTime();
                            packet.putLong(8,currTime);
                            DatagramPacket sendingPacket = new DatagramPacket(packet.array(), packetData.length, ip, outPort);
                            outSock.send(sendingPacket);
                            System.out.format("snd %d - A - - 1 0 1\n",currTime);
                            packSent++;
                            buffer.get(0).getTimer().cancel();
                            buffer.remove(0);

                        }
                        else{
                            continue;
                        }
                    }
                    else{
                        //received ack
                        if(flag == 1){
                            System.out.format("rcv %d - A - - %d %d %d\n",timePack,seqNo,dataLen,ackRec);
                            if(ackRec > lAckRec){
                                //System.out.println("ACK REC: " + ackRec);
                                lAckRec = ackRec;
                                Iterator<Integer> it = buffer.keySet().iterator();
                                while(it.hasNext()){
                                    int seqNoRem = it.next(); 
                                    if(seqNoRem < ackRec){
                                        //System.out.println("REMOVED: " + seqNoRem);
                                        buffer.get(seqNoRem).getTimer().cancel();
                                        it.remove();
                                    }
                                }

                                //System.out.println("BUFFER SIZE: " + buffer.size());
                                //System.out.println("BUFFER : " + buffer.keySet());

                            }
                            else if (ackRec == lAckRec){
                                if(dupCount < 2){
                                    dupCount++;
                                    dupAcks++;
                                }
                                else if(dupCount>=2){
                                    dupAcks++;                                    
                                    //retransmit(ackRec);
                                    dupCount = 0;
                                }
                                 
                            }
                            else{
                                continue;
                            }
                        }
                        
                        //received fin + ack
                        if(flag == 3){
                            System.out.format("rcv %d - A F - 1 0 %d\n",timePack,ackRec);
                            //System.out.println("SENT ACK FOR FIN");
                            byte[] packetData = createPacket(fSeqNum+1,seqNo+1,1,null);
                            ByteBuffer packet = ByteBuffer.wrap(packetData);
                            long currTime = System.nanoTime();
                            packet.putLong(8,currTime);
                            DatagramPacket sendingPacket = new DatagramPacket(packet.array(), packetData.length, ip, outPort);
                            outSock.send(sendingPacket);
                            System.out.format("snd %d - A - - %d 0 2\n",currTime,ackRec);
                            packSent++;

                            printStats();

                            System.exit(1);
                        }
                    }
                    
                }
            }catch(Exception e){
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    public void printStats(){
        System.out.println("\nAmount of Data Transferred: " + fSeqNum);
        System.out.println("Number of Packets Sent: " + packSent);
        System.out.println("Number of Out-of-Sequence Packets Discarded: 0");
        System.out.println("Number of Packets Discarded Due to Incorrect Checksum: 0");
        System.out.println("Number of Retransmissions: " + numRetrans);
        System.out.println("Number of Duplicate Acknowledgements: " + dupAcks + "\n");
    }

    public byte[] createPacket(int seqNum,int ack, int flag, byte[] data){
        
        if(data != null){
            int dataLen = data.length;

            ByteBuffer packet = ByteBuffer.wrap(new byte[24 + dataLen]);
            packet.putInt(seqNum);
            packet.putInt(ack);
            packet.putLong(0);

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
            packet.putLong(0);

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
        System.out.println("CHECKSUM : " + checksum );
        System.out.println("-----------------------------");

    }

}