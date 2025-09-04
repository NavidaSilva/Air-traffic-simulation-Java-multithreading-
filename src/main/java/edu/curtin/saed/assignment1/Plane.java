
package edu.curtin.saed.assignment1;

/** Plane model with minimal mutable state required for the simulation. */
public class Plane
{
    public enum State { ON_GROUND, IN_FLIGHT, SERVICING }
    public final int id;
    public volatile State state = State.ON_GROUND;

    // Current stable location (used when on ground)
    public volatile double x, y;

    // Where the plane is parked
    public volatile int currentAirport;

    // Flight in-progress fields
    public volatile int originAirport = -1;
    public volatile int destAirport = -1;
    public volatile double startX, startY, endX, endY;
    public volatile long departMillis;
    public volatile long durationMillis;
    public volatile double distance;

    public Plane(int id, int atAirport, double x, double y)
    {
        this.id = id;
        this.currentAirport = atAirport;
        this.x = x; this.y = y;
    }
}
