import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DFTeste extends Thread {

    private int sizeList = 0;
    private final long margin;
    private final String trace;
    // para armazenar os tempos dos heartbeats de cada nodo 
    private Queue<Long>[] A;
    private final long delta = 100000000; //nanoseconds = 100 miliseconds
    private long EA = 0; 

    public DFTeste(int sizeList, long margin, String trace) {
        this.sizeList = sizeList;
        this.margin = margin;
        this.trace = trace;
        this.A = new Queue[10];
        for (int j = 0; j < 10; j++) {
            this.A[j] = new LinkedList<>(); //uma lista para cada nodo
        }
    }
    public void execute() {
        final int numSets = 3;

        // listas para guardar, para cada nodo, as taxas de cada set
        List<Double>[] taxasPorSet = new ArrayList[10];
        for (int i = 0; i < 10; i++) {
            taxasPorSet[i] = new ArrayList<>();
        }

        // acumula, ao longo dos 3 runs, os totais para acurácia e contagem de erros
        long[] totalTempoAtrasoAg = new long[10];
        long[] totalTimeNanoAg     = new long[10];
        long[] totalErrosAg        = new long[10];

        OutputStream os;
        BufferedWriter bw;
        OutputStreamWriter osw;
        FileInputStream inputStream;
        Scanner sc ;
        String[] stringArray; // para ler a linha
        long[] timeout;
        int sizeList, id = 0, lin = 1;
        long ts = 0; 
        // ts -> timestamp atual
        long[] tPrevious; // para armazenar o último tempo

        //Variaveis declaradas para completar o codigo
        int[] atrasos = new int[10];            // Quantas vezes o no foi suspeito
        long[] tempoAtraso = new long[10];      // Tempo de atraso acumulado
        long[] tempoInicial = new long[10];     // Primeiro heartbeat
        long[] tempoFinal = new long[10];       // Ultimo heartbeat
        boolean[] primeiro = new boolean[10];   // Marca se e o primeiro heartbeat

        // Roda 3 vezes (3 sets)
        for (int run = 0; run < numSets; run++) {
            // zera tudo antes de cada set
            for (int i = 0; i < 10; i++) {
                atrasos[i]      = 0;
                tempoAtraso[i]  = 0;
                tempoInicial[i] = 0;
                tempoFinal[i]   = 0;
                primeiro[i]     = true;
                A[i].clear();
            }

        for (int i = 0; i < 10; i++){
            primeiro[i] = true;
        }

        String line;
        timeout = new long[10];
        tPrevious = new long[10];

        try {
            inputStream = new FileInputStream(trace);
            sc = new Scanner(inputStream, "UTF-8");
            while (sc.hasNextLine()) {
                line = sc.nextLine();
                
                stringArray = line.split(" ");
                id = Integer.parseInt(stringArray[0]);
                ts = Long.parseLong(stringArray[3]);
                sizeList = A[id].size();
               
                EA = (long) computeEA(sizeList, id);
                timeout[id] = EA + margin;

                if (primeiro[id]) {
                    tempoInicial[id] = ts; // Timestamp do primeiro heartbeat do no
                    primeiro[id] = false;
                }

                // System.out.println(">" + ts + " - " + timeout[id]);
                if ((ts > timeout[id]) && (!A[id].isEmpty())) {
                    atrasos[id]++;
                    tempoAtraso[id] += (ts - timeout[id]);
                    System.out.println(">" + ts + " - " + timeout[id]);
                    /// heartbeat chegou depois da estimativa
                    /// coloca como suspeito
                } 

                tempoFinal[id] = ts;

                if (A[id].size() == this.sizeList) {
                    A[id].poll();
                }
                A[id].add(ts);
                tPrevious[id] = ts; // último ts do id
                lin++;
            }
            sc.close();
            inputStream.close();
        } catch (IOException | NumberFormatException ex) {
                Logger.getLogger(DFTeste.class.getName())
                      .log(Level.SEVERE, null, ex);
        }

            // eof

            // METRICAS

            // Numero de erros do detector ==> atrasos[id]

            // Tempo de erro do detector ==> tempoAtraso[id]

            // Tempo total ==> tempoFinal[id] - tempoInicial[id]

            //Taxa de erro ==> atrasos[id] / ((tempoFinal[id]) - tempoInicial[id] / 1000000000.0)

            // Probabilidade de Acuracia ==> tempoAtraso[id] / (tempoFinal[id] - tempoInicial[id])
            /*
                    taxa de erro do id = número de erros do id / tempo total  / 1000000000 
                    pa do id = tempo de erro do id / tempo total do id 
                    pa = probabilidade de acurácia
            
            tempo total !- tempo total do id
            */
            
            /// aqui fazer laço para os nodos e gravar dados em arquivo

            for ( id = 0; id < 10; id++){
                    if (tempoInicial[id] != 0) {
                        long tempoTotal = tempoFinal[id] - tempoInicial[id];
                        double taxaErro = (double) atrasos[id] / (tempoTotal / 1000000000);
                        taxasPorSet[id].add(taxaErro);
                        totalTempoAtrasoAg[id] += tempoAtraso[id];
                        totalTimeNanoAg[id] += tempoTotal;
                        totalErrosAg[id] += atrasos[id];
                    }
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter("saida.log", true))) {
                NumberFormat f = new DecimalFormat("0.000000000000000");
                for ( id = 0; id < 10; id++){
                    List<Double> L = taxasPorSet[id];
                    if (!L.isEmpty()) {
                        double soma = 0;
                        for (double t : L)
                            soma += t;
                        double taxaMedia = soma / L.size();

                        double pa = (double) totalTempoAtrasoAg[id] / totalTimeNanoAg[id];
                        double acuracia = 1 - pa;

                        String linha = String.format(
                            "%d;%d;%d;%d;%d;%d;%s;%s",
                            id,
                            this.sizeList,
                            margin,
                            totalErrosAg[id],
                            totalTempoAtrasoAg[id],
                            totalTimeNanoAg[id],
                            f.format(taxaMedia),
                            f.format(acuracia)
                        );
                        writer.write(linha);
                        writer.newLine();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        
    }

    public double computeEA(long heartbeat, int id) {
        //id of node
        //heartbeat = highest number of heartbeat sequence received
        double tot = 0, avg = 0;
        int i = 0;
        long ts;
        try {
            NumberFormat f = new DecimalFormat("0.0");
            Queue<Long> q = new LinkedList();
            q.addAll(A[id]);
            while (!q.isEmpty()) {
                ts = q.poll();
                i++;
                tot += ts - (delta * i);
            }
            if (heartbeat> 0) {
                avg = ((1 / (double) heartbeat) *
                        ((double) tot)) + (((double) heartbeat + 1) * delta);
            }
            return avg;
        } catch (Exception e) {
            System.out.println("ERRO " + e.getMessage());
            return 0;
        }
    }

}