
package edu.curtin.saed.assignment1;

/** Simple immutable airport record */
public class Airport
{
    public final int id;
    public final double x;
    public final double y;

    public Airport(int id, double x, double y)
    {
        this.id = id; this.x = x; this.y = y;
    }
}