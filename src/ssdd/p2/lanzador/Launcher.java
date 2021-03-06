/*
 * AUTORES: Juan Vela Garcia / Marta Frias Zapater
 * NIA: 643821 / 535621
 * FICHERO: Launcher.java
 * TIEMPO: 11 horas
 * DESCRIPCION: Permite construir la infraestructura necesaria para que un 
 * cliente pueda determinar los numeros primos de un cierto 
 * intervalo, dividiendolo en subintervalos y preguntando de 
 * manera concurrente a tantos servidores de calculo como indique.
 */

package ssdd.p2.lanzador;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ssdd.p2.cliente.ClienteThread;
import ssdd.p2.herramientas.GestorIntervalos;
import ssdd.p2.herramientas.GestorIp;
import ssdd.p2.herramientas.Intervalo;
import ssdd.p2.herramientas.Registrador;
import ssdd.p2.herramientas.Resultado;
import ssdd.p2.herramientas.UnionIntervalos;
import ssdd.p2.interfase.Register;
import ssdd.p2.interfase.Worker;
import ssdd.p2.interfase.WorkerFactory;
import ssdd.p2.servidor.WorkerFactoryServer;
import ssdd.p2.servidor.WorkerServer;

/**
 * Permite construir la infraestructura necesaria para que un cliente pueda
 * determinar los numeros primos de un cierto intervalo, dividiendolo en
 * subintervalos y preguntando de manera concurrente a tantos servidores de
 * calculo como indique.
 * 
 * La sintaxis es la siguiente:
 * 
 * -c [ipRegistroRMI] //servidor de calculo -a [ipRegistroRMI] //servidor de
 * asignacion -u min max numServidores [ipRegistroRMI] //cliente
 * 
 * Cada servidor de calculo obtiene los primos en un intervalo dado. El servidor
 * de asignacion encuentra (busca en el registro RMI) tantos servidores de
 * calculo como le pide el cliente. El cliente proporciona el minimo y el maximo
 * de un intervalo, y el numero de servidores de calculo que necesita para
 * obtener el resultado.
 * 
 * @author Juan Vela y Marta Frias
 * 
 */
public class Launcher {

    /** Numero de elementos en intervalo */
    private static final int NUM_ELEMS_INTERVALO = 1000;

    /** Numero de servidores de calculo procesando un intervalo */
    private static int pendientesPorLeer = 0;

    /** Tiempo de ejecucion medio de los servidores de calculo */
    private static double tiempoMedio = 0.0;

    /**
     * Devuelve una cadena de texto con la sintaxis de las acciones disponibles
     * 
     * @return sintaxis de las acciones disponibles
     */
    private static String sintaxisParams() {

        String sintaxis = "Opciones: \n";
        sintaxis += "-c [ipRegistroRMI]\n";
        sintaxis += "-a [ipRegistroRMI]\n";
        sintaxis += "-u min max numServidores  [ipRegistroRMI]\n";

        return sintaxis;
    }

    /**
     * Realiza el reparto inicial de intervalos entre los workers. Devuelve el
     * numero de workers activos (a los que se les ha enviado trabajo)
     * 
     * @param workers - lista de servidores de calculo
     * @param cliente - lista de subprocesos encargados de atender a cada uno de
     *            los servidores de calculo
     * @param intervalo - gestor de intervalos
     */
    private static int repartoInicial(ArrayList<Worker> workers,
            ClienteThread[] cliente, GestorIntervalos intervalo) {

        int i = 0;

        // para cada uno de los servidores de calculo obtenidos
        for (Worker w : workers) {

            // crea un subproceso del cliente (concurrente) para atenderlo
            cliente[i] = new ClienteThread(w);

            cliente[i].setNumElems(NUM_ELEMS_INTERVALO);

            // si quedan subintervalos por asignar
            // -> enviar trabajo al servidor
            if (!intervalo.haAcabado()) {

                UnionIntervalos subIntervalo = intervalo
                        .getSubIntervalo(cliente[i].getNumElems());

                cliente[i].setIntervalo(subIntervalo);

                Thread t = new Thread(cliente[i]);
                t.start();
                i++;

            } else {
                // si pide mas workers que elementos en el intervalo,
                // los que sobren se quedan sin hacer nada
            }
        }

        return i;
    }

    /**
     * Almacena los resultados de un servidor de calculo y si quedan
     * subintervalos sin procesar, se le asigna uno de ellos al mencionado
     * servidor.
     *
     * @param i - identificador del servidor de calculo
     * @param cliente - lista de subprocesos encargados de atender a cada uno de
     *            los servidores de calculo
     * @param tiempoWorker - ultimo tiempo del worker
     * @param tiempoTotalWorkers - cuenta total del tiempo del worker
     * @param listaPrimos - lista de primos
     * @param intervalo - gestor de intervalos
     * @param pendientes - lista de intervalos sin procesar (worker fallido)
     */
    private static void repartoDinamico(int i, ClienteThread[] cliente,
            double[] tiempoWorker, double[] tiempoTotalWorkers,
            LinkedList<Integer> listaPrimos, GestorIntervalos intervalo,
            LinkedList<UnionIntervalos> pendientes) {

        // almacenar resultado
        Resultado resultado = cliente[i].getResultado();
        ArrayList<Integer> listaParcial = resultado.getResultado();

        tiempoWorker[i] = resultado.getTiempo();

        // si ya hay un tiempo medio
        if (tiempoMedio > 0.0) {

            // actualizar numElems
            int numElems = cliente[i].getNumElems();
            double tiempo = tiempoWorker[i];
            double factor = tiempo / tiempoMedio;
            numElems = (int) (numElems / factor);
            cliente[i].setNumElems(numElems);

            tiempoTotalWorkers[i] += tiempoWorker[i];

        } else {
            tiempoTotalWorkers[i] = tiempoWorker[i];
        }

        // a�adir resultados parciales a lista global
        for (Integer primo : listaParcial) {
            listaPrimos.add(primo);
        }

        // establecer como atendido
        pendientesPorLeer--;
        cliente[i].setAtendido();

        // si quedan elementos en el intervalo
        if (!intervalo.haAcabado()) {

            // enviar mas trabajo
            int numElems = cliente[i].getNumElems();
            UnionIntervalos subIntervalo = intervalo.getSubIntervalo(numElems);
            cliente[i].setIntervalo(subIntervalo);

            Thread t = new Thread(cliente[i]);
            t.start();
            pendientesPorLeer++;
        }

        // si NO quedan elementos en el intervalo pero algun
        // worker ha fallado, hacer su trabajo
        else if (pendientes.size() > 0) {

            // si alguno ha fallado, a�adir a la cola de pendientes
            UnionIntervalos subIntervalo = pendientes.getFirst();
            pendientes.removeFirst();
            cliente[i].setIntervalo(subIntervalo);
            Thread t = new Thread(cliente[i]);
            t.start();
        }
    }

    /**
     * Calcula el tiempo medio utilizado por los servidores de calculo y el
     * numero de elementos a asignar a cada uno.
     *
     * @param numWorkers - numero de servidores de calculo disponible
     * @param cliente -lista de subprocesos encargados de atender a cada uno de
     *            los servidores de calculo
     * @param tiempoWorker - ultimo tiempo del worker
     */
    private static void calcularTiempoMedio(int numWorkers,
            ClienteThread[] cliente, double[] tiempoWorker) {

        // comprobar tiempos en busca de poder calcular una media
        int cuenta = 0;

        // comprueba si se han recibido todos los tiempos
        for (int j = 0; j < numWorkers; j++) {
            if (tiempoWorker[j] != 0.0) {
                cuenta++;
            }
        }

        // solo calcula la media si ya se han recibido todos los
        // tiempos
        if (cuenta == numWorkers) {

            for (int j = 0; j < numWorkers; j++) {
                tiempoMedio += tiempoWorker[j];
            }

            tiempoMedio = tiempoMedio / (double) numWorkers;

            // calcular nuevo numero de elementos para cada worker
            for (int j = 0; j < numWorkers; j++) {
                int numElems = cliente[j].getNumElems();
                double tiempo = tiempoWorker[j];
                double factor = tiempo / tiempoMedio;
                numElems = (int) (numElems / factor);
                cliente[j].setNumElems(numElems);
            }
        }
    }

    /**
     * Cliente que calcula los numeros primos que hay en un intervalo de enteros
     * [min,max], a traves de un numero [n] de servidores de calculo y los
     * muestra por pantalla.
     * 
     * @param min minimo entero del intervalo
     * @param max maximo entero del intervalo
     * @param n numero de servidores de calculo
     * @param ipRegistro direccion ip donde se encuentra el servidor de registro
     */
    private static void crearCliente(int min, int max, int numWorkers,
            String ipRegistro) {

        GestorIntervalos intervalo = new GestorIntervalos(
                new Intervalo(min, max));
        LinkedList<Integer> listaPrimos = new LinkedList<Integer>();

        long tIni = System.nanoTime();

        try {

            // contacta con el registro RMI y obtiene la referencia a un
            // servidor de asignaciom
            Registry registry = LocateRegistry.getRegistry(ipRegistro);
            WorkerFactory wf = (WorkerFactory) registry
                    .lookup("WorkerFactoryServer");

            // solicita servidores de calculo
            ArrayList<Worker> workers = wf.dameWorkers(numWorkers);

            // numWorkers almacena el numero de workers devueltos
            // (puede ocurrir que haya menos de los que se piden)
            numWorkers = workers.size();
            ClienteThread[] cliente = new ClienteThread[numWorkers];

            // prepara gestion de fallos
            LinkedList<UnionIntervalos> pendientes = new LinkedList<UnionIntervalos>();
            LinkedList<Worker> workersFallidos = new LinkedList<Worker>();

            // reparte el trabajo entre los workers y obtiene el numero de
            // workers trabajando
            pendientesPorLeer = repartoInicial(workers, cliente, intervalo);

            // actualiza el numero de workers empleados
            // (puede ocurrir que haya workers sin trabajo asignado)
            numWorkers = pendientesPorLeer;

            // prepara gestion de tiempos de ejecucion
            double[] tiempoWorker = new double[numWorkers];
            double[] tiempoTotalWorkers = new double[numWorkers];

            int i = 0;
            boolean fin = false, error = false;

            // mientras quede algo por hacer (queden servidores y subintervalos)
            while (!fin) {

                // si alguno ha acabado y no ha sido atendido
                // --> atender y mandar mas trabajo (si queda)
                if (cliente[i].isAcabado() && !cliente[i].isAtendido()) {

                    // reparte cantidad trabajo en funcion de la respuesta
                    // ante el ultimo envio
                    repartoDinamico(i, cliente, tiempoWorker,
                            tiempoTotalWorkers, listaPrimos, intervalo,
                            pendientes);

                }

                // si alguno ha fallado, atenderlo y recoger su intervalo
                // para que otro lo compruebe
                else if (cliente[i].isFallido() && !cliente[i].isAtendido()) {

                    // worker caido
                    cliente[i].setAtendido();
                    pendientes.addLast(cliente[i].getIntervalo());
                    workersFallidos.add(cliente[i].getWorker());
                }

                // si todavia no se ha calculado el tiempo medio
                // -> intentar calcularlo
                if (tiempoMedio == 0.0) {
                    calcularTiempoMedio(numWorkers, cliente, tiempoWorker);
                }

                // barrido circular
                i = (i + 1) % numWorkers;

                // Si no quedan resultados por procesar
                // -> terminar
                if (pendientesPorLeer == 0) {
                    fin = true;
                }

                // si han fallado todos
                // -> terminar con error
                else if (workersFallidos.size() == numWorkers) {
                    error = true;
                    fin = true;
                }
            }

            // si se ha podido completar el calculo, presenta resultados
            if (!error) {

                for (int j = 0; j < numWorkers; j++) {
                    System.out.printf("Worker %d -> Tiempo total = %.5f ms\n",
                            j, tiempoTotalWorkers[j]);
                }

                long tTotal = System.nanoTime() - tIni;

                // imprime tiempos de ejecucion
                System.out.printf("Tiempo total de ejecucion = %.5f ms\n",
                        tTotal / 1000000.0);

                Collections.sort(listaPrimos);

                System.out.printf(
                        "Se han encontrado %d primos"
                                + " en el intervalo (%d, %d).\n",
                        listaPrimos.size(), min, max);

                // imprime lista de primos por pantalla
                for (Integer p : listaPrimos) {
                    System.out.println(p);
                }

            }

            // si no se ha podido completar, informa del error
            else {
                System.err.println(
                        "ERROR: conexion perdida con TODOS los workers.");
            }

        } catch (Exception e) {
            System.err.println("ERROR: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * Crea y registra un servidor de asignacion en el registro RMI
     * 
     * @param ipRegistro direccion ip donde se encuentra el servidor de registro
     */
    private static void crearServidorAsignacion(String ipRegistro) {

        GestorIp gestorIp = new GestorIp();
        String ipLocal = gestorIp.getIpObtenida();

        System.getProperties().setProperty("java.rmi.server.hostname", ipLocal);

        final String nombre = "WorkerFactoryServer";

        try {

            final Registry registroRMI = LocateRegistry.getRegistry(ipRegistro);
            final WorkerFactoryServer wfs = new WorkerFactoryServer(ipRegistro);

            // registro remoto, usar registrador
            if (ipRegistro != null) {
                
                if (ipLocal != null) {
                    final Register registrador = (Register) registroRMI
                        .lookup(Registrador.RMI_NAME);

                registrador.registrarServidorAsignacion(wfs, nombre);
                
                } else {
                    System.err.println(
                            "ERROR: No se ha podido obtener una direccion IP valida");
                }
            }

            // registro local
            else {

                registroRMI.rebind(nombre, wfs);
            }

            System.out.printf(
                    "Servidor de asignacion (%s)" + " registrado con exito.\n",
                    nombre);

        } catch (Exception e) {
            System.err.println("ERROR: " + e.toString());
            e.printStackTrace();
        }

    }

    /**
     * Crea y registra un servidor de calculo en el registro RMI, teniendo en
     * cuenta para la eleccion del nombre los que ya han sido registrados.
     * 
     * @param ipRegistro direccion ip donde se encuentra el servidor de registro
     */
    private static void crearServidorCalculo(String ipRegistro) {

        GestorIp gestorIp = new GestorIp();
        String ipLocal = gestorIp.getIpObtenida();

        System.getProperties().setProperty("java.rmi.server.hostname", ipLocal);

        String nombre = "Worker";

        try {
            Registry registroRMI = LocateRegistry.getRegistry(ipRegistro);
            WorkerServer workerServer = new WorkerServer();

            // registro remoto, usar registrador
            if (ipRegistro != null) {

                if (ipLocal != null) {
                    final Register registrador = (Register) registroRMI
                            .lookup(Registrador.RMI_NAME);

                    String nombreTmp = "";
                    nombreTmp = registrador
                            .registrarServidorCalculo(workerServer, nombre);

                    System.out.printf("Servidor de calculo (%s)"
                            + " registrado con exito.\n", nombreTmp);
                } else {
                    System.err.println(
                            "ERROR: No se ha podido obtener una direccion IP valida");
                }
            }

            // registro local
            else {

                boolean registrado = false;
                String[] nombresRegistrados = registroRMI.list();

                int cuenta = 0;

                final Pattern workerPatt = Pattern.compile("Worker\\d+");

                // cuenta workers registrados
                for (int i = 0; i < nombresRegistrados.length; i++) {
                    Matcher esWorker = workerPatt
                            .matcher(nombresRegistrados[i]);
                    if (esWorker.matches()) {
                        cuenta++;
                    }
                }

                // elige siguiente numero para el nuevo worker
                cuenta++;

                // mientras no se haya registrado
                while (!registrado) {

                    try {
                        String nombreTmp = nombre + (cuenta);
                        registroRMI.bind(nombreTmp, workerServer);
                        registrado = true;

                        System.out.printf(
                                "Servidor de calculo (%s)"
                                        + " registrado con exito.\n",
                                nombreTmp);

                    } catch (AlreadyBoundException e) {
                        cuenta++;
                    }
                }

            }

        } catch (Exception e) {
            System.err.println("ERROR: " + e.toString());
        }
    }

    /**
     * Crea y registra un registrador de otros servidores (cuya ubicacion es
     * distinta a la del registro). El registrador debe compartir ubicacion con
     * el registro.
     */
    private static void crearRegistrador() {
        Registry registroRmi;

        try {
            registroRmi = LocateRegistry.getRegistry(null);
            Registrador registrador = new Registrador(registroRmi);
            registroRmi.rebind(Registrador.RMI_NAME, registrador);

            Launcher lanzador = new Launcher();
            synchronized (lanzador) {
                try {
                    lanzador.wait();
                } catch (InterruptedException e) {
                    // Terminar
                }
            }
        } catch (RemoteException e) {
            System.err.println("ERROR: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * Inicia y gestiona todo el sistema distribuido de calculo de numero
     * primos. Reconoce los parametros introducidos y actua en consecuencia. En
     * caso de error informa de ello por pantalla
     * 
     */
    public static void main(String[] args) {

        if (args.length > 0) {

            String opcion = args[0];

            // SERVIDOR DE CALCULO
            if (opcion.equals("-c")) {
                String host = null;

                if (args.length <= 2) {
                    if (args.length == 2) {
                        host = args[1];
                    }
                    crearServidorCalculo(host);

                } else if (args.length > 2) {
                    System.err
                            .println("ERROR: Numero de parametros incorrecto.");
                    System.err.printf(sintaxisParams());
                }
            }

            // SERVIDOR DE ASIGNACION
            else if (opcion.equals("-a")) {
                String host = null;

                if (args.length <= 2) {
                    if (args.length == 2) {
                        host = args[1];
                    }
                    crearServidorAsignacion(host);

                } else if (args.length > 2) {
                    System.err
                            .println("ERROR: Numero de parametros incorrecto.");
                    System.err.printf(sintaxisParams());
                }
            }

            // CLIENTE
            else if (opcion.equals("-u")) {

                if (args.length == 4 || args.length == 5) {
                    try {
                        String host = null;
                        int min = Integer.parseInt(args[1]);
                        int max = Integer.parseInt(args[2]);
                        int n = Integer.parseInt(args[3]);

                        if (args.length == 5) {
                            host = args[4];
                        }
                        if (min < max) {
                            crearCliente(min, max, n, host);
                        } else {
                            System.err.println(
                                    "ERROR: sintaxis incorrecta. (min >= max)");
                            System.err.printf(sintaxisParams());
                        }

                    } catch (NumberFormatException e) {
                        System.err.println("ERROR: sintaxis incorrecta.");
                        System.err.printf(sintaxisParams());
                    }
                } else {
                    System.err
                            .println("ERROR: Numero de parametros incorrecto.");
                    System.err.printf(sintaxisParams());
                }

            }

            // REGISTRADOR
            else if (opcion.equals("-r")) {

                if (args.length == 1) {

                    crearRegistrador();

                } else {
                    System.err
                            .println("ERROR: Numero de parametros incorrecto.");
                    System.err.printf(sintaxisParams());
                }

            } else {
                System.err.println("ERROR: sintaxis incorrecta.");
                System.err.printf(sintaxisParams());
            }
        } else {
            System.err.println("ERROR: sintaxis incorrecta.");
            System.err.printf(sintaxisParams());
        }
    }
}
