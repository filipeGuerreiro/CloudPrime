import BIT.highBIT.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Arrays;
import java.util.Timer;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import mss.*;

public class CloudPrimeInstrumentation {
    
    private static final int  MAX_THREADS      = 2048;
    private static final long METRIC_THRESHOLD = 1000000000L;
    
    private static PrintStream out = null;
    
    // locks for accessing the instrumentation metrics thread-safely
    //private static Lock[] _mutex = new Lock[MAX_THREADS];
    //static { Arrays.fill(_mutex, new ReentrantLock(true)); }
    
    // instrumentation metrics
    private static long[] _startTime      = new long[MAX_THREADS]; // default init = 0
    private static long[] _loadcount      = new long[MAX_THREADS]; // default init = 0
    private static long[] _registeredLoad = new long[MAX_THREADS]; // default init = 0
    
    private static MSS _mss;
    
    /* main reads in all the files class files present in the input directory,
     * instruments only IntFactorization, and outputs it to the specified output directory.
     */
    public static void main(String argv[]) {
        File file_in = new File(argv[0]);
        String infilenames[] = file_in.list();
        
        for (int i = 0; i < infilenames.length; i++) {
            String infilename = infilenames[i];
            
            if (infilename.equals("IntFactorization.class")) { 
				// create class info object
				ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);
				
                // loop through all the routines
                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();
                    
                    // instrument routine calcPrimeFactors to measure metrics
                    if(routine.getMethodName().equals("calcPrimeFactors")) {
                        
                        // calculate loads
                        for (Enumeration instrs = (routine.getInstructionArray()).elements(); instrs.hasMoreElements(); ) {
							Instruction instr = (Instruction) instrs.nextElement();
							int opcode=instr.getOpcode();
                            short instr_type = InstructionTable.InstructionTypeTable[opcode];
                            if (instr_type == InstructionTable.LOAD_INSTRUCTION) {
                                instr.addBefore("CloudPrimeInstrumentation", "LSCount", 0);
                            }
							
						}
                    }
                    
                    // when calcPrimeFactors finishes, method factorize is called and prints result to file
                    else if(routine.getMethodName().equals("factorize")) {
                        routine.addBefore("CloudPrimeInstrumentation", "acquireLocks", 0);
                        
                        routine.addAfter("CloudPrimeInstrumentation", "releaseLocks", 0);
                    }
                }
                
                // create instrumented .class
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
            
            // instrument webserver class main method for MSS init
            if (infilename.equals("WebServer.class")) { 
				
				ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);
				
                // loop through all the routines
                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();
                    
                    if(routine.getMethodName().equals("main")) {
                        routine.addAfter("CloudPrimeInstrumentation", "initMSS", 0);
                    }
                }
                
                // create instrumented .class
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
    

    public static void acquireLocks(int nil) {
        long threadId = Thread.currentThread().getId();
        int index = getIndex(threadId);
        
        //_mutex[index].lock();
        
        _loadcount[index] = 0L;
        _startTime[index] = System.currentTimeMillis();
    }
    

    public static void releaseLocks(int nil) {
        long threadId = Thread.currentThread().getId();
        int index = getIndex( threadId );
        
        // remove metrics from mss
        removeMetrics( threadId );
        
        // release locks
        _loadcount[index] = 0L;
        _startTime[index] = 0L;
        //_mutex[index].unlock();
    }
    
    // always called after acquireLocks
	public static void LSCount(int nil) {
        long threadId = Thread.currentThread().getId();
        
        long loadCount = _loadcount[ getIndex( threadId ) ]++;
        if(loadCount > METRIC_THRESHOLD) { sendMetrics( loadCount ); }
	}
    
    private static void sendMetrics(long loadCount) {
        long threadId = Thread.currentThread().getId();
        int index = getIndex(threadId);
        long endTime = System.currentTimeMillis();
        long duration = (endTime - _startTime[index]);
        
        long load =  loadCount / duration;
        System.out.println("Update metrics: "+load);
        _loadcount[index] = 0L;
        _startTime[index] = endTime;
        
        // send metrics to dynamoDB
        if(_registeredLoad[index] == 0L) { _mss.updateMetrics( load ); }
        else { _mss.updateMetrics( load - _registeredLoad[index] ); }
        
        _registeredLoad[index] = load;
    }
    
    private static void removeMetrics(long threadId) {
        int index = getIndex(threadId);
        
        // remove metrics from dynamoDB
        if(_registeredLoad[index] != 0L) {
            _mss.updateMetrics( _registeredLoad[index] * -1 );
            _registeredLoad[index] = 0L;
        }
    }
    
    private static int getIndex(long id) {
        return (int) id % MAX_THREADS;
    }
    
    
    public static void initMSS(int nil) {
        _mss = new MSS();
        _mss.initMetricStorage();
    }
    
    
    private static synchronized void writeToFile(String text) {
        File file = new File("metrics.txt");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file, true); // true for append mode
            writer.write(text + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) try { writer.close(); } catch (IOException ignore) {}
        }
    }
    
}

