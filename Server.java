package trabalhoredes;

import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.Arrays;
  
public class Server { 
    static final int HEADER = 4;
    static final int TAMANHO_PACOTE = 1000 + HEADER;
    static final int PORTA_SERVIDOR = 8002;
    static final int ACK_PORT = 8003;
 
    //construtor
    public Server(String fn, int sport, int wnd) {


     // public Servidor(int sport, int ACK_PORT, String caminho) {
        DatagramSocket inSocket, outSocket;
        System.out.println("Servidor: porta de entrada: " + sport + ".");
 
        int lastNumSeq = -1;
        int nextNumSeq = 0;  //proximo numero de sequencia
        boolean transfer = false;  //flag caso a transferencia nao for completa
        String path = "./" + fn;
        //criando sockets
        try {
            inSocket = new DatagramSocket(sport);
            outSocket = new DatagramSocket();
            System.out.println("Servidor Conectado...");
            try {
                byte[] receiveData = new byte[wnd + HEADER]; //tamanho pacote
                DatagramPacket receivePackage = new DatagramPacket(receiveData, receiveData.length);
 
                FileOutputStream fos = null;
 
                while (!transfer) {
                    int i = 0;
                    inSocket.receive(receivePackage);
                    InetAddress ipAddress = receivePackage.getAddress();
 
                    int numSeq = ByteBuffer.wrap(Arrays.copyOfRange(receiveData, 0, HEADER)).getInt();
                    System.out.println("Servidor: Numero de sequencia recebido " + numSeq);
 
                    //se o pacote for recebido em ordem
                    if (numSeq == nextNumSeq) {
                        //se for ultimo pacote (sem dados), enviar ack de encerramento
                        if (receivePackage.getLength() == HEADER) {
                            byte[] ackPackage = gerarPacote(-2);     //ack de encerramento
                            outSocket.send(new DatagramPacket(ackPackage, ackPackage.length, ipAddress, ACK_PORT));
                            transfer = true;
                            System.out.println("Servidor: Todos pacotes foram recebidos! file criado!");
                        } else {
                            nextNumSeq = numSeq + TAMANHO_PACOTE - HEADER;  //atualiza proximo numero de sequencia
                            byte[] ackPackage = gerarPacote(nextNumSeq);
                                outSocket.send(new DatagramPacket(ackPackage, ackPackage.length, ipAddress, ACK_PORT));
                                System.out.println("Servidor: Ack enviado " + nextNumSeq);
                          }
 
                        //se for o primeiro pacote da transferencia 
                        if (numSeq == 0 && lastNumSeq == -1) {
                            //cria file    
                            File file = new File(path);
                            if (!file.exists()) {
                                file.createNewFile();
                            }
                            fos = new FileOutputStream(file);
                        }
                        //escreve dados no file
                        fos.write(receiveData, HEADER, receivePackage.getLength() - HEADER);
 
                        lastNumSeq = numSeq; //atualiza o ultimo numero de sequencia enviado
                    } else {    //se pacote estiver fora de ordem, mandar duplicado
                        byte[] ackPackage = gerarPacote(lastNumSeq);
                        outSocket.send(new DatagramPacket(ackPackage, ackPackage.length, ipAddress, ACK_PORT));
                        System.out.println("Servidor: Ack duplicado enviado " + lastNumSeq);
                    }
 
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            } finally {
                inSocket.close();
                outSocket.close();
                System.out.println("Servidor: Socket de entrada fechado!");
                System.out.println("Servidor: Socket de saida fechado!");
            }
        } catch (SocketException e1) {
            e1.printStackTrace();
        }
    }
    //fim do construtor
 
    //gerar pacote de ACK
    public byte[] gerarPacote(int numAck) {
        byte[] numAckBytes = ByteBuffer.allocate(HEADER).putInt(numAck).array();
        ByteBuffer packageBuffer = ByteBuffer.allocate(HEADER);
        packageBuffer.put(numAckBytes);
        return packageBuffer.array();
    }
 
    public static void main(String[] args) {
        int udpPort = 0;
        int wnd = 0;
        try {
            udpPort = Integer.parseInt(args[1]);
            wnd = Integer.parseInt(args[2]);
        }
        catch(NumberFormatException nfe) {
            System.exit(1);
        }
        String fileName = args[0];

        Server server = new Server(fileName, udpPort, wnd);
    }
}