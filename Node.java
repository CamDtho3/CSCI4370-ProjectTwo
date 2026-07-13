// Node.java - one node of the RA expression tree.
// A node is EITHER a base table (a leaf) OR an operator with children.
package relation;

import java.util.ArrayList;
import java.util.List;

public class Node {
    public String op;        // "proj","sel","join","union","minus"   (null if this is a table)
    public String arg;       // the text inside [ ... ]   (null for tables, union, minus)
    public String table;     // table name   (null for operator nodes)
    public List<Node> children = new ArrayList<>();

    /** True if this node is a base table (a leaf). */
    public boolean isTable() { return table != null; }

    /** Label shown when printing, e.g.  proj [fname , lname]  or just  r */
    public String label() { return isTable() ? table : op + (arg == null ? "" : " [" + arg + "]"); }
}
