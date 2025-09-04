/**
 * DO NOT MODIFY THIS CODE! You could lose marks.
 */

package edu.curtin.saed.assignment1;
import java.util.*;
import java.util.logging.Logger;

public class StandardPlaneServicing
{
    private static final Logger logger = Logger.getLogger(StandardPlaneServicing.class.getName());
    private static final int MIN_WAIT = 1000;
    private static final int MAX_WAIT = 5000;

    public static void service(int thisAirport, int thisPlane) throws InterruptedException
    {
        var random = new Random();
        int time = MIN_WAIT + random.nextInt(MAX_WAIT - MIN_WAIT);
        logger.info(() -> String.format("Begin servicing plane #%d at airport #%d...", thisPlane, thisAirport));
        try
        {
            Thread.sleep(time);
            logger.info(() -> String.format(
                "Plane servicing complete (plane #%d at airport #%d, in %d ms)", thisPlane, thisAirport, time));
        }
        catch(InterruptedException e)
        {
            logger.warning(() -> String.format(
                "Plane servicing INTERRUPTED (plane #%d at airport #%d, in %d ms)", thisPlane, thisAirport, time));
            throw e;
        }
    }
}
