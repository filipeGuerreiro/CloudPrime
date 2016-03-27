import BIT.highBIT.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;


public class CloudPrimeInstrumentation {
    private static PrintStream out = null;
    private static int i_count = 0, b_count = 0, m_count = 0;
    
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
                        routine.addBefore("CloudPrimeInstrumentation", "mcount", new Integer(1));
                    
                        for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                            BasicBlock bb = (BasicBlock) b.nextElement();
                            bb.addBefore("CloudPrimeInstrumentation", "count", new Integer(bb.size()));
                        }
                    }
                    // when calcPrimeFactors finishes, print result to file
                    else if(routine.getMethodName().equals("factorize")) {
                        routine.addAfter("CloudPrimeInstrumentation", "printICount", ci.getClassName());
                    }
                }
                //ci.addAfter("CloudPrimeInstrumentation", "printICount", ci.getClassName());
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
    
    // TODO: print ID of thread <- finds out how many requests are being handled by this server
    // how long the request has been running
    public static synchronized void printICount(String foo) {
        String result = i_count + " instructions in " + b_count + " basic blocks were executed in " + m_count + " methods.";
        System.out.println(result);

        File file = new File("metrics.txt");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(result);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) try { writer.close(); } catch (IOException ignore) {}
        }
    }
    

    public static synchronized void count(int incr) {
        i_count += incr;
        b_count++;
    }

    public static synchronized void mcount(int incr) {
		m_count++;
    }
}

