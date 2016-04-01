import BIT.highBIT.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Arrays;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class CloudPrimeInstrumentation {
    
    private static final int MAX_THREADS = 50;
    
    private static PrintStream out = null;
    
    // locks for accessing the instrumentation metrics thread-safely
    private static Lock[] _mutex = new Lock[MAX_THREADS];
    static { Arrays.fill(_mutex, new ReentrantLock(true)); }
    
    // instrumentation metrics
    private static long[] _startTime = new long[MAX_THREADS]; // default init = 0   
    private static long[] _loadcount = new long[MAX_THREADS]; // default init = 0
    
    
    /* main reads in all the files class files present in the input directory,
     * instruments only IntFactorization, and outputs it to the specified output directory.
     */
    public static void main(String argv[]) {
        File file_in = new File(argv[0]);
        String infilenames[] = file_in.list();
        
        for (int i = 0; i < infilenames.length; i++) {
            String infilename = infilenames[i];
            
            if (infilename.equals("IntFactorization.class")) { // WebServer.class
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
                        routine.addBefore("CloudPrimeInstrumentation", "startTimer", 0);
                        
                        //routine.addAfter("CloudPrimeInstrumentation", "endTimer", ci.getClassName());
                        routine.addAfter("CloudPrimeInstrumentation", "printICount", 0);
                    }
                }
                
                // create instrumented .class
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
    
    // acquires locks here
    public static void startTimer(int nil) {
        long threadId = Thread.currentThread().getId();
        
        _mutex[getIndex(threadId)].lock();
        _startTime[getIndex(threadId)] = System.currentTimeMillis();
    }
    
    // always called after startTimer
	public static void LSCount(int nil) {
        long threadId = Thread.currentThread().getId();
        
        _loadcount[getIndex(threadId)]++;
	}
    
    // print ID of thread <- finds out how many requests are being handled by this server
    // print how long the request has been running
    public static void printICount(int nil) {
        long threadId = Thread.currentThread().getId();
        long endTime = System.currentTimeMillis();
        long duration = (endTime - _startTime[getIndex(threadId)]); // milliseconds
        
        //TODO: find value of job request Number!
        String result = "Thread " + threadId + " || " + duration + " milliseconds || " + _loadcount[getIndex(threadId)] + " loads";
        
        // release locks here
        _loadcount[getIndex(threadId)] = 0;
        _startTime[getIndex(threadId)] = 0;
        _mutex[getIndex(threadId)].unlock();

        // synchronized method
        writeToFile(result);
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
    
    private static int getIndex(long id) {
        return (int) id % MAX_THREADS;
    }
    
}

