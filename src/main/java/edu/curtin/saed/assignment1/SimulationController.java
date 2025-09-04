
package edu.curtin.saed.assignment1;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates airports, planes, request generators, dispatchers, movement, and servicing.
 */
public class SimulationController
{
    private final double W, H, SPEED;
    private final int NA, NP;

    private final GridArea area;
    private final Label statusText;
    private final TextArea log;

    private final Random rand = new Random();

    // Model
    private final Map<Integer, Airport> airports = new HashMap<>();
    private final Map<Integer, Plane> planes = new ConcurrentHashMap<>();

    // Icons
    private final Map<Integer, GridAreaIcon> airportIcons = new HashMap<>();
    private final Map<Integer, GridAreaIcon> planeIcons = new ConcurrentHashMap<>();

    // Queues per airport
    private final List<BlockingQueue<Integer>> requestQueues = new ArrayList<>(); // dest airport IDs
    private final List<BlockingQueue<Integer>> availablePlanes = new ArrayList<>(); // plane IDs available at airport

    // Executors / threads
    private final List<Thread> requestGenerators = new ArrayList<>(); // StandardFlightRequests go()
    private final List<Thread> requestReaders = new ArrayList<>();    // BufferedReader readers
    private ExecutorService dispatcherPool;                            // one worker per airport
    private ExecutorService servicePool;                               // plane servicing pool

    // Animation timer (FX thread)
    private Timeline timeline;

    // Statistics
    private volatile int inFlight = 0;
    private volatile int servicing = 0;
    private volatile int completed = 0;

    // Lifecycle
    private volatile boolean running = false;

    public SimulationController(double w, double h, int na, int np, double speed,
                                GridArea area, Label statusText, TextArea log)
    {
        this.W = w; this.H = h; this.NA = na; this.NP = np; this.SPEED = speed;
        this.area = area; this.statusText = statusText; this.log = log;

        // Build airports
        for(int a = 0; a < NA; a++)
        {
            double x = rand.nextDouble() * (W - 1); 
            double y = rand.nextDouble() * (H - 1);
            Airport ap = new Airport(a, x, y);
            airports.put(a, ap);
            requestQueues.add(new LinkedBlockingQueue<>());
            availablePlanes.add(new LinkedBlockingQueue<>());
        }

        // Build planes and initially park at airports (available)
        int planeId = 0;
        for(int a = 0; a < NA; a++)
        {
            Airport ap = airports.get(a);
            for(int i = 0; i < NP; i++)
            {
                Plane p = new Plane(planeId++, ap.id, ap.x, ap.y);
                planes.put(p.id, p);
                availablePlanes.get(a).add(p.id);
            }
        }

        // Draw airports (always shown)
        for(Airport ap : airports.values())
        {
            GridAreaIcon icon = new GridAreaIcon(ap.x, ap.y, 0.0, 1.0,
                loadImage("airport.png"), "AP " + ap.id);
            icon.setShown(true);
            airportIcons.put(ap.id, icon);
            area.getIcons().add(icon);
        }

        // Prepare hidden plane icons (shown only when in flight)
        for(Plane p : planes.values())
        {
            GridAreaIcon icon = new GridAreaIcon(p.x, p.y, 0.0, 1.0,
                loadImage("plane.png"), "PL " + p.id);
            icon.setShown(false);
            planeIcons.put(p.id, icon);
            area.getIcons().add(icon);
        }

        area.requestLayout();

        // Animation timeline: 20 FPS (>= 10 updates per second requirement)
        timeline = new Timeline(new KeyFrame(Duration.millis(50), e -> onFrame()));
        timeline.setCycleCount(Timeline.INDEFINITE);

        // Executors
        dispatcherPool = Executors.newFixedThreadPool(NA, r -> {
            Thread t = new Thread(r, "Dispatcher"); t.setDaemon(true); return t; });
        servicePool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Service"); t.setDaemon(true); return t; });

        appendLog("Ready. Press Start to begin simulation.\n");
        updateStatus();
    }

    // <-------------------------------------- Lifecycle -------------------------------------->
    public synchronized void start()
    {
        if(running) return;
        running = true;

        // Launch request generators + readers
        for(int a = 0; a < NA; a++)
        {
            final int origin = a;
            StandardFlightRequests sfr = new StandardFlightRequests(NA, origin);

            Thread gen = new Thread(() -> {
                try { sfr.go(); }
                catch(InterruptedException ignored) { /* graceful end */ }
            }, "SFR-Go-" + origin);
            gen.setDaemon(true);
            gen.start();
            requestGenerators.add(gen);

            BufferedReader br = sfr.getBufferedReader();
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while(!Thread.currentThread().isInterrupted() && (line = br.readLine()) != null)
                    {
                        int dest = Integer.parseInt(line.trim());
                        requestQueues.get(origin).put(dest);
                    }
                }
                catch(IOException | InterruptedException ignored) { /* end */ }
            }, "SFR-Read-" + origin);
            reader.setDaemon(true);
            reader.start();
            requestReaders.add(reader);

            // Per-airport dispatcher worker from queue -> plane -> flight
            dispatcherPool.submit(() -> dispatcherLoop(origin));
        }

        timeline.playFromStart();
        appendLog("Simulation started.\n");
    }

    public synchronized void end()
    {
        if(!running) return;
        running = false;

        // Stop animation immediately: everything stays as-is on screen
        Platform.runLater(() -> timeline.stop());

        // Interrupt request generators & readers
        for(Thread t : requestGenerators) t.interrupt();
        for(Thread t : requestReaders) t.interrupt();

        // Stop dispatchers quickly
        dispatcherPool.shutdownNow();
        // Stop service tasks quickly (interrupt to end StandardPlaneServicing where possible)
        servicePool.shutdownNow();

        appendLog("Simulation ended.\n");
        updateStatus();
    }

    public void shutdownAndExit()
    {
        end();
        Platform.exit();
        System.exit(0);
    }

    // <-------------------------------------- Dispatcher -------------------------------------->
    private void dispatcherLoop(int origin)
    {
        try
        {
            while(running && !Thread.currentThread().isInterrupted())
            {
                int dest = requestQueues.get(origin).take();
                int planeId = availablePlanes.get(origin).take();
                Plane p = planes.get(planeId);
                if(p == null) continue;
                dispatchFlight(p, origin, dest);
            }
        }
        catch(InterruptedException ignored) { /* end */ }
    }

    private void dispatchFlight(Plane p, int origin, int dest)
    {
        Airport ap0 = airports.get(origin);
        Airport ap1 = airports.get(dest);
        synchronized(p)
        {
            p.state = Plane.State.IN_FLIGHT;
            p.originAirport = origin;
            p.destAirport = dest;
            p.startX = ap0.x; p.startY = ap0.y;
            p.endX = ap1.x;   p.endY = ap1.y;
            p.departMillis = System.currentTimeMillis();
            double dx = p.endX - p.startX, dy = p.endY - p.startY;
            p.distance = Math.hypot(dx, dy);
            p.durationMillis = (long) ((p.distance / SPEED) * 1000.0);
        }
        inFlight++;
        appendLog(String.format("DEPART: Plane %d from AP %d -> AP %d\n", p.id, origin, dest));
        Platform.runLater(() -> {
            GridAreaIcon icon = planeIcons.get(p.id);
            icon.setShown(true);
            icon.setRotation(angleDegrees(p.startX, p.startY, p.endX, p.endY));
            area.requestLayout();
            updateStatus();
        });
    }

    // <-------------------------------------- Animation frame --------------------------------->
    private void onFrame()
    {
        long now = System.currentTimeMillis();
        boolean needsLayout = false;

        for(Plane p : planes.values())
        {
            if(p.state == Plane.State.IN_FLIGHT)
            {
                double t = Math.min(1.0, (now - p.departMillis) / (double)p.durationMillis);
                double x = lerp(p.startX, p.endX, t);
                double y = lerp(p.startY, p.endY, t);
                GridAreaIcon icon = planeIcons.get(p.id);
                icon.setPosition(x, y);
                needsLayout = true;

                if(t >= 1.0)
                {
                    // Arrived -> start servicing in background
                    p.state = Plane.State.SERVICING;
                    icon.setShown(false);
                    inFlight--; servicing++; completed++;
                    int arrivedAirport = p.destAirport;
                    p.currentAirport = arrivedAirport;
                    appendLog(String.format("LAND: Plane %d at AP %d\n", p.id, arrivedAirport));
                    final int planeId = p.id;
                    servicePool.submit(() -> doService(arrivedAirport, planeId));
                }
            }
        }

        if(needsLayout) area.requestLayout();
        updateStatus();
    }

    private void doService(int airportId, int planeId)
    {
        try {
            StandardPlaneServicing.service(airportId, planeId);
        } catch (InterruptedException ignored) { /* end early */ }
        finally
        {
            Plane p = planes.get(planeId);
            if(p == null) return;
            synchronized(p)
            {
                p.state = Plane.State.ON_GROUND;
                Airport ap = airports.get(airportId);
                p.x = ap.x; p.y = ap.y; // snap to airport
                p.originAirport = airportId;
                p.destAirport = -1;
            }
            servicing--;
            // Make plane available at this airport for next request
            availablePlanes.get(airportId).offer(planeId);
            Platform.runLater(() -> updateStatus());
        }
    }

    // <-------------------------------------- Helpers ----------------------------------------->
    private void updateStatus()
    {
        statusText.setText(String.format("In-Flight: %d | Servicing: %d | Completed: %d", inFlight, servicing, completed));
    }

    private void appendLog(String s)
    {
        Platform.runLater(() -> log.appendText(s));
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double angleDegrees(double x0, double y0, double x1, double y1)
    {
        double ang = Math.toDegrees(Math.atan2(y1 - y0, x1 - x0));
        return ang;
    }

    private static javafx.scene.image.Image loadImage(String name)
    {
        InputStream is = App.class.getClassLoader().getResourceAsStream(name);
        if(is == null) throw new IllegalStateException("Missing resource: " + name +
                " (place airport.png and plane.png on classpath, e.g., src/main/resources)");
        return new javafx.scene.image.Image(is);
    }
}
