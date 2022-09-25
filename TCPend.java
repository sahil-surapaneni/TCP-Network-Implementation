import java.io.*;
import java.util.*;

public class TCPend {
    public static void main(String[] args){
        int port = 0;
        String ip = "";
        int remotePort = -1;
        String file = "";
        int mtu = 0;
        int sws = 0;

        if(args.length != 12 && args.length != 8){
            System.out.println(args.length);
            System.err.println("Invalid Number of Arguments Specified");
            System.exit(0); 
        }

        for(int i = 0; i<args.length; i++){
            String arg = args[i];
            if(arg.equals("-p")){
                port = Integer.parseInt(args[++i]);
            }
            if(arg.equals("-s")){
                ip = args[++i];
            }
            if(arg.equals("-a")){
                remotePort = Integer.parseInt(args[++i]);
            }
            if(arg.equals("-f")){
                file = args[++i];
            }
            if(arg.equals("-m")){
                mtu = Integer.parseInt(args[++i]);
            }
            if(arg.equals("-c")){
                sws = Integer.parseInt(args[++i]);
            }
        }

        if(remotePort != -1){
            
            new TCPsender(port,ip,remotePort,file,mtu,sws);
        }
        else{
           
            new TCPreceiver(port,file,mtu,sws);
        }
        
    }
}