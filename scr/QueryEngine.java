// QueryEngine.java (the file you work in)
// =====================================================================================
// You implement TWO methods here:  eval()  (Part D)  and  optimize()  (Part E).
// The parser and tree-printing live in RAParser.java (Part C) : you don't touch those.
//
// USEFUL LINKS
//   Relational algebra basics : https://www.tutorialspoint.com/distributed_dbms/distributed_dbms_relational_algebra_query_optimization.htm
//   Selection pushdown (E)    : https://www.geeksforgeeks.org/dbms/query-optimization-in-relational-algebra/
//   Pushdown, worked example  : https://15445.courses.cs.cmu.edu/fall2021/notes/13-optimization1.pdf   (see "predicate pushdown")
//   Java Map (get/put)        : https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html
// =====================================================================================
package relation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import relation.KeyType;
import relation.RAParser;
import relation.Table;

public class QueryEngine {

    // The "catalog": maps a table name (like "r") to the actual Table object.
    private Map<String, Table> tables = new HashMap<>();
    public void put(String name, Table t) { tables.put(name, t); }

    // =================================================================================
    //  PART D - eval(Node n):  RUN the query tree and return the answer Table.
    //
    //  BIG IDEA (bottom-up / post-order): to run a node, first run its CHILD/CHILDREN,
    //  then apply THIS node's operator to the result. eval() calls itself on children :
    //  that recursion walks the whole tree for you.
    //
    //  Worked order for  proj(sel(join(r,s))) :  join runs first -> then sel -> then proj.
    //
    //  The leaf case is done below as a model. Fill in each operator by following the
    //  SAME shape: "evaluate the child(ren), then call the matching Table method."
    //
    //  Table methods you will call (all already work): sel(String), proj(String),
    //  join(String,String,Table), union(Table), minus(Table).
    //  RAParser gives you helpers already - see each TODO.
    // =================================================================================
    public Table eval(Node n) {

        // ---- LEAF (a base table): just return it from the catalog. (DONE-> your model.) ----
        if (n.isTable()) {
            return tables.get(n.table);
        }

        Node left = n.children.get(0);              // every operator has at least one child

        if (n.op.equals("sel")) {
            Table in = eval(left);   
            
            // 1) run the child first
            // 2) apply select. The query writes "id = 5"; Table.sel wants "id == 5",
            //    so pass it through RAParser.condition(...) which fixes the "=".
             return in.sel(RAParser.condition(n.arg));
        }

        if (n.op.equals("proj")) {
            // The arg looks like "fname , lname"; Table.proj wants "fname lname"
            // (no commas). RAParser.attrs(...) does that clean-up for you.
            return eval(left).proj(RAParser.attrs(n.arg));
        }

        if (n.op.equals("join")) {
            Node right = n.children.get(1);         // join has TWO children
            // The arg looks like "r.a = s.b". RAParser.joinLeft gives "a", joinRight gives "b".
            Table L = eval(left);
            Table R = eval(right);

            return L.join(
                RAParser.joinLeft(n.arg),
                RAParser.joinRight(n.arg),
                R
            );
        }

        if (n.op.equals("union")) {
            // union/minus have no [ ] arg :  just two children.
            return eval(left).union(eval(n.children.get(1)));
        }

        if (n.op.equals("minus")) {
            return eval(left).minus(eval(n.children.get(1)));
        }

        return null;   // <- once every case above returns, this line is never reached
    }

    // =================================================================================
    //  PART E : optimize(Node n):  rewrite the tree so it runs faster, then return it.
    //
    //  HEURISTIC: "filter early." A sel that sits ABOVE a join can often be moved BELOW
    //  it, onto the branch whose table has the column the sel tests. Filtering first
    //  makes the join do far less work : and the answer is exactly the same.
    //
    //  LEGAL move: sel[p] over join(L,R) can go onto L if p's column is in L's schema,
    //              or onto R if p's column is in R's schema. If p uses columns from BOTH
    //              sides, LEAVE it above the join.
    //  (Read: https://www.geeksforgeeks.org/dbms/query-optimization-in-relational-algebra/ )
    //
    //  HOW (one way):
    //   1) Recurse into the children first (optimize them), rebuilding the node.
    //   2) If this node is a "sel" whose only child is a "join":
    //        - find the column the predicate tests  ->  n.arg.trim().split(" ")[0]
    //        - if schema(leftChild)  contains it  ->  put the sel on the left child
    //        - else if schema(rightChild) contains it -> put the sel on the right child
    //        - else leave the sel where it is
    //   3) Repeat until the tree stops changing (a "fixpoint": loop while it keeps moving).
    //
    //  To BUILD a new node in code:
    //        Node m = new Node(); m.op = "sel"; m.arg = "..."; m.children.add(childNode);
    //  To get a table's columns for the schema test:  tables.get(name).columns()
    //  A schema() helper is stubbed for you below.
    // =================================================================================
    public Node optimize(Node n) {
        // Base case
        if (n == null || n.isTable()) {
            return n;
        }

        // Child Optimization
        for (int i = 0; i < n.children.size(); i++) {
            n.children.set(i, optimize(n.children.get(i)));
        }

        if (n.op.equals("sel")
                && n.children.size() == 1
                && n.children.get(0).op.equals("join")) {

            Node joinNode = n.children.get(0);

            Node leftChild  = joinNode.children.get(0);
            Node rightChild = joinNode.children.get(1);

            String column = n.arg.trim().split("\\s+")[0];

            if (column.contains(".")) {
                column = column.substring(column.indexOf('.') + 1);
            }

            Set<String> leftSchema  = schema(leftChild);
            Set<String> rightSchema = schema(rightChild);

            boolean belongsToLeft  = leftSchema.contains(column);
            boolean belongsToRight = rightSchema.contains(column);

            // Pushes onto left
            if (belongsToLeft && !belongsToRight) {
                Node pushedSelection = new Node();

                pushedSelection.op  = "sel";
                pushedSelection.arg = n.arg;
                pushedSelection.children.add(leftChild);

                joinNode.children.set(0, pushedSelection);

                return joinNode;
            }

            // Pushes onto right
            if (belongsToRight && !belongsToLeft) {
                Node pushedSelection = new Node();

                pushedSelection.op  = "sel";
                pushedSelection.arg = n.arg;
                pushedSelection.children.add(rightChild);

                joinNode.children.set(1, pushedSelection);

                return joinNode;
            }
        }

        return n;
    }

    /** OPTIONAL helper for Part E: the set of column names a subtree produces.
     *  leaf -> that table's columns;  proj -> its projected columns;
     *  join -> both children's columns combined;  sel/union/minus -> same as child 0. */
    private Set<String> schema(Node n) {

    if (n.isTable()) {
        return new HashSet<>(
            Arrays.asList(tables.get(n.table).columns())
        );
    }

    if (n.op.equals("proj")) {
        return new HashSet<>(
            Arrays.asList(
                RAParser.attrs(n.arg).split("\\s+")
            )
        );
    }

    if (n.op.equals("join")) {
        Set<String> result =
            new HashSet<>(schema(n.children.get(0)));

        result.addAll(
            schema(n.children.get(1))
        );

        return result;
    }

    if (n.op.equals("sel")
            || n.op.equals("union")
            || n.op.equals("minus")) {

        return schema(n.children.get(0));
    }

    return new HashSet<>();
}

    // =================================================================================
    //  main : builds the tables, parses the query, prints the tree, runs eval.
    //  (Don't need to change this to get started.)
    // =================================================================================
    public static void main(String[] args) {
        QueryEngine db = new QueryEngine();

        Table r = new Table("r", "a fname lname", "Integer String String", "a");
        r.add(new Comparable[]{1, "Ann", "Lee"});
        r.add(new Comparable[]{2, "Bob", "Ng"});
        r.add(new Comparable[]{3, "Cy",  "Ito"});
        r.add(new Comparable[]{4, "Di",  "Roe"});

        Table s = new Table("s", "b id dept", "Integer Integer String", "b");
        s.add(new Comparable[]{1, 5, "CS"});
        s.add(new Comparable[]{2, 7, "Math"});
        s.add(new Comparable[]{3, 5, "Bio"});
        s.add(new Comparable[]{4, 9, "Econ"});

        db.put("r", r);
        db.put("s", s);

        String query = "proj ( [ fname , lname ] , sel ( [ id = 5 ] , join ( [ r.a = s.b ] , r , s ) ) )";
        Node root = RAParser.parse(query);

        System.out.println("--- RA Expression Tree ---");
        RAParser.printTree(root, "", "\u2514\u2500\u2500 ");

        System.out.println("\n--- Result ---");
        Table result = db.eval(root);
        if (result != null) result.show();
        else System.out.println("[TODO] implement eval() to see the answer");

        // Range query (uses the TreeMap index): rows of s whose key b is in [2, 3]
        System.out.println("\n--- Range query: s where key b in [2, 3] ---");
        Table rng = s.range(new KeyType(2), new KeyType(3));
        if (rng != null && rng.rowCount() > 0) rng.show();
        else System.out.println("[TODO] implement range()");
    }
}