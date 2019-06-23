package trabalhoredes;

/**
 *
 * @authors Catarina Ribeiro, Leonardo Cavalcante, Leonardo Portugal, Victor
 * Meireles
 *
 */
import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.Arrays;
  
public class Servidor { 
    static final int CABECALHO = 4;
    static final int TAMANHO_PACOTE = 1000 + CABECALHO;
    static final int PORTA_SERVIDOR = 8002;
    static final int PORTA_ACK = 8003;
 
    //construtor
    public Servidor(int inPort, int outPort, String caminho) {
        DatagramSocket inSocket, outSocket;
        System.out.println("Servidor: porta de entrada: " + inPort + ", " + "porta de destino: " + outPort + ".");
 
        int lastNumSeq = -1;
        int nextNumSeq = 0;  //proximo numero de sequencia
        boolean transfer = false;  //flag caso a transferencia nao for completa
 
        //criando sockets
        try {
            inSocket = new DatagramSocket(inPort);
            outSocket = new DatagramSocket();
            System.out.println("Servidor Conectado...");
            try {
                byte[] receiveData = new byte[TAMANHO_PACOTE];
                DatagramPacket receivePackage = new DatagramPacket(receiveData, receiveData.length);
 
                FileOutputStream fos = null;
 
                while (!transfer) {
                    int i = 0;
                    inSocket.receive(receivePackage);
                    InetAddress ipAddress = receivePackage.getAddress();
 
                    int numSeq = ByteBuffer.wrap(Arrays.copyOfRange(receiveData, 0, CABECALHO)).getInt();
                    System.out.println("Servidor: Numero de sequencia recebido " + numSeq);
 
                    //se o pacote for recebido em ordem
                    if (numSeq == nextNumSeq) {
                        //se for ultimo pacote (sem dados), enviar ack de encerramento
                        if (receivePackage.getLength() == CABECALHO) {
                            byte[] ackPackage = gerarPacote(-2);     //ack de encerramento
                            outSocket.send(new DatagramPacket(ackPackage, ackPackage.length, ipAddress, outPort));
                            transfer = true;
                            System.out.println("Servidor: Todos pacotes foram recebidos! file criado!");
                        } else {
                            nextNumSeq = numSeq + TAMANHO_PACOTE - CABECALHO;  //atualiza proximo numero de sequencia
                            byte[] ackPackage = gerarPacote(nextNumSeq);
                                outSocket.send(new DatagramPacket(ackPackage, ackPackage.length, ipAddress, outPort));
                                System.out.println("Servidor: Ack enviado " + nextNumSeq);
                          }
 
                        //se for o primeiro pacote da transferencia 
                        if (numSeq == 0 && lastNumSeq == -1) {
                            //cria file    
                            File file = new File(caminho);
                            if (!file.exists()) {
                                file.createNewFile();
                            }
                            fos = new FileOutputStream(file);
                        }
                        //escreve dados no file
                        fos.write(receiveData, CABECALHO, receivePackage.getLength() - CABECALHO);
 
                        lastNumSeq = numSeq; //atualiza o ultimo numero de sequencia enviado
                    } else {    //se pacote estiver fora de ordem, mandar duplicado
                        byte[] ackPackage = gerarPacote(lastNumSeq);
                        outSocket.send(new DatagramPacket(ackPackage, ackPackage.length, ipAddress, outPort));
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
        byte[] numAckBytes = ByteBuffer.allocate(CABECALHO).putInt(numAck).array();
        ByteBuffer packageBuffer = ByteBuffer.allocate(CABECALHO);
        packageBuffer.put(numAckBytes);
        return packageBuffer.array();
    }
 
    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);
        System.out.println("----------------------------------------------SERVIDOR----------------------------------------------");
        System.out.print("Digite o diretorio do file a ser criado. (Ex: C:/Users/Diego/Documents/): ");
        String diretorio = teclado.nextLine();
        System.out.print("Digite o nome do file a ser criado: (Ex: letra.txt): ");
        String nome = teclado.nextLine();
 
        Servidor servidor = new Servidor(PORTA_SERVIDOR, PORTA_ACK, diretorio + nome);
    }
}