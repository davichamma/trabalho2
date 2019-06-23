package trabalhoredes;

/**
 * @authors Catarina Ribeiro, Leonardo Cavalcante, Leonardo Portugal, Victor
 * Meireles
 *
 */
import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import static java.lang.Thread.sleep;
 
public class Client {
 
    static final int HEADER = 4;
    static final int TAMANHO_PACOTE = 1000;  // (numSeq:4, dados=1000) Bytes : 1004 Bytes total
    static final int TAMANHO_JANELA = 10;
    static final int VALOR_TIMER = 1000;
    static final int PORTA_SERVIDOR = 8002;
    static final int PORTA_ACK = 8003;
 
    int base;    // numero da janela
    int nextNumSeq;   //proximo numero de sequencia na janela
    String path;     //diretorio + nome do file
    List<byte[]> packageList;
    Timer timer;
    Semaphore light;
    boolean transfer;
 
    //construtor
    public Client(String fileName, String ipAddress, ing udpPort)

    public Cliente(int outPort, int inPort, String path, String ipAddress) {
        base = 0;
        nextNumSeq = 0;
        this.path = path;
        packageList = new ArrayList<>(TAMANHO_JANELA);
        transfer = false;
        DatagramSocket outSocket, inSocket;
        light = new Semaphore(1);
        System.out.println("Cliente: porta de destino: " + outPort + ", porta de entrada: " + inPort + ", path: " + path);
 
        try {
            //criando sockets
            outSocket = new DatagramSocket();
            inSocket = new DatagramSocket(inPort);
 
            //criando threads para processar os dados
            ThreadEntrada tEntrada = new ThreadEntrada(inSocket);
            ThreadSaida tSaida = new ThreadSaida(outSocket, outPort, inPort, ipAddress);
            tEntrada.start();
            tSaida.start();
 
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    //fim do construtor
 
    public class Timer extends TimerTask {
 
        public void run() {
            try {
                light.acquire();
                System.out.println("Cliente: Tempo expirado!");
                nextNumSeq = base;  //reseta numero de sequencia
                light.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
 
    //para iniciar ou parar o Timer
    public void handleTimer(boolean newTimer) {
        if (timer != null) {
            timer.cancel();
        }
        if (newTimer) {
            timer = new Timer();
            timer.schedule(new Timer(), VALOR_TIMER);
        }
    }
 
    public class ThreadSaida extends Thread {
 
        private DatagramSocket outSocket;
        private int outPort;
        private InetAddress ipAddress;
        private int inPort;
 
        //construtor
        public ThreadSaida(DatagramSocket outSocket, int outPort, int inPort, String ipAddress) throws UnknownHostException {
            this.outSocket = outSocket;
            this.outPort = outPort;
            this.inPort = inPort;
            this.ipAddress = InetAddress.getByName(ipAddress);
        }
 
        //cria o pacote com numero de sequencia e os dados
        public byte[] generatePackage(int numSeq, byte[] dataByte) {
            byte[] numSeqByte = ByteBuffer.allocate(HEADER).putInt(numSeq).array();
            ByteBuffer bufferPackage = ByteBuffer.allocate(HEADER + dataByte.length);
            bufferPackage.put(numSeqByte);
            bufferPackage.put(dataByte);
            return bufferPackage.array();
        }
 
        public void run() {
            try {
                FileInputStream fis = new FileInputStream(new File(path));
 
                try {
                    while (!transfer) {    //envia pacotes se a janela nao estiver cheia
                        if (nextNumSeq < base + (TAMANHO_JANELA * TAMANHO_PACOTE)) {
                            light.acquire();
                            if (base == nextNumSeq) {   //se for primeiro pacote da janela, inicia Timer
                                handleTimer(true);
                            }
                            byte[] sentData = new byte[HEADER];
                            boolean lastNumSeq = false;
 
                            if (nextNumSeq < packageList.size()) {
                                sentData = packageList.get(nextNumSeq);
                            } else {
                                byte[] dataBuffer = new byte[TAMANHO_PACOTE];
                                int dataSize = fis.read(dataBuffer, 0, TAMANHO_PACOTE);
                                if (dataSize == -1) {   //sem dados para enviar, envia pacote vazio 
                                    lastNumSeq = true;
                                    sentData = generatePackage(nextNumSeq, new byte[0]);
                                } else {    //ainda ha dados para enviar
                                    byte[] dataBytes = Arrays.copyOfRange(dataBuffer, 0, dataSize);
                                    sentData = generatePackage(nextNumSeq, dataBytes);
                                }
                                packageList.add(sentData);
                            }
                            //enviando pacotes
                            outSocket.send(new DatagramPacket(sentData, sentData.length, ipAddress, outPort));
                            System.out.println("Cliente: Numero de sequencia enviado " + nextNumSeq);
 
                            //atualiza numero de sequencia se nao estiver no fim
                            if (!lastNumSeq) {
                                nextNumSeq += TAMANHO_PACOTE;
                            }
                            light.release();
                        }
                        sleep(5);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    handleTimer(false);
                    outSocket.close();
                    fis.close();
                    System.out.println("Cliente: Socket de saida fechado!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
 
    public class ThreadEntrada extends Thread {
 
        private DatagramSocket inSocket;
 
        //construtor
        public ThreadEntrada(DatagramSocket inSocket) {
            this.inSocket = inSocket;
        }
 
        //retorna ACK
        int getnumAck(byte[] pacote) {
            byte[] numAckBytes = Arrays.copyOfRange(pacote, 0, HEADER);
            return ByteBuffer.wrap(numAckBytes).getInt();
        }
 
        public void run() {
            try {
                byte[] receiveData = new byte[HEADER];  //pacote ACK sem dados
                DatagramPacket receivePackage = new DatagramPacket(receiveData, receiveData.length);
                try {
                    while (!transfer) {
                        inSocket.receive(receivePackage);
                        int numAck = getnumAck(receiveData);
                        System.out.println("Cliente: Ack recebido " + numAck);
                        //se for ACK duplicado
                        if (base == numAck + TAMANHO_PACOTE) {
                            light.acquire();
                            handleTimer(false);
                            nextNumSeq = base;
                            light.release();
                        } else if (numAck == -2) {
                            transfer = true;
                        } //ACK normal
                        else {
                            base = numAck + TAMANHO_PACOTE;
                            light.acquire();
                            if (base == nextNumSeq) {
                                handleTimer(false);
                            } else {
                                handleTimer(true);
                            }
                            light.release();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    inSocket.close();
                    System.out.println("Cliente: Socket de entrada fechado!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
 
    public static void main(String[] args) {
        if (args.length <= 4) {
            throw 
        }
        //nome do file. ip, porta, tam janela, rtt, mss, dupck, lp
        String fileName = args[0];
        ipAddress = args[1];
        udpPort = args[2];

        Client client = new Client(fileName, ipAddress, udpPort)

        Scanner teclado = new Scanner(System.in);
        System.out.println("----------------------------------------------CLIENTE-----------------------------------------------");
        System.out.print("Digite o endereco do servidor: ");
        String ipAddress = teclado.nextLine();
        System.out.print("Digite o diretorio do file a ser enviado. (Ex: C:/Users/Diego/Documents/): ");
        String diretorio = teclado.nextLine();
        System.out.print("Digite o nome do file a ser enviado: (Ex: letra.txt): ");
        String nome = teclado.nextLine();
 
        Cliente cliente = new Cliente(PORTA_SERVIDOR, PORTA_ACK, diretorio + nome, ipAddress);
    }
}