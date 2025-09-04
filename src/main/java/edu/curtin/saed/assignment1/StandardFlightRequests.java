/**
 * DO NOT MODIFY THIS CODE! You could lose marks.
 */

package edu.curtin.saed.assignment1;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class StandardFlightRequests
{
    private static final Logger logger = Logger.getLogger(StandardFlightRequests.class.getName());
    private static final int MIN_WAIT = 1000;
    private static final int MAX_WAIT = 5000;
    private final int nAirports;
    private final int thisAirport;
    private final PipedOutputStream pipeOut;
    private final PipedInputStream pipeIn;

    public StandardFlightRequests(int nAirports, int thisAirport)
    {
        this.nAirports = nAirports;
        this.thisAirport = thisAirport;
        pipeOut = new PipedOutputStream();
        pipeIn = new PipedInputStream();
        try
        {
            pipeOut.connect(pipeIn);
        }
        catch(IOException e)
        {
            // Theoretically shouldn't occur.
            throw new AssertionError(e);
        }
    }

    public BufferedReader getBufferedReader()
    {
        return new BufferedReader(new InputStreamReader(pipeIn));
    }

    public void go() throws InterruptedException
    {
        var random = new Random();
        try(var out = new PrintStream(pipeOut))
        {
            while(true)
            {
                int dest = random.nextInt(nAirports);
                if(dest != thisAirport)
                {
                    out.println(dest);
                    Thread.sleep(MIN_WAIT + random.nextInt(MAX_WAIT - MIN_WAIT));
                }
            }
        }
        finally
        {
            // System.out.printf("Flight requests from airport %d stopped", thisAirport);
            logger.info(() -> String.format("Flight requests from airport %d stopped", thisAirport));
        }
    }
}
