//     javac -d out relation/*.java
//     java -cp out relation.TestRunner
// It runs the code against known inputs and prints PASS/FAIL + a score.
// (Reads results via the table's private 'tuples' field)
package relation;

import java.lang.reflect.Field;
import java.util.*;

public class TestRunner {
    static int pass = 0, total = 0;

    @SuppressWarnings("unchecked")
    static Set<String> rows(Table t) {                 // result rows as a set of strings
        if (t == null) return null;
        try {
            Field f = Table.class.getDeclaredField("tuples"); f.setAccessible(true);
            List<Comparable[]> ts = (List<Comparable[]>) f.get(t);
            Set<String> s = new HashSet<>();
            for (Comparable[] row : ts) s.add(Arrays.toString(row));
            return s;
        } catch (Exception e) { return null; }
    }
    static void check(String part, String name, Table got, Set<String> want) {
        total++;
        Set<String> g = rows(got);
        int n = (got == null) ? -1 : got.rowCount();
        boolean ok = g != null && g.equals(want) && n == want.size();   // count catches duplicates
        String why = g == null ? "null/exception"
                   : (n != want.size() ? "row count " + n + " (duplicates?)" : "got=" + g);
        System.out.printf("[%s] %-6s %-34s %s%n", ok ? "PASS" : "FAIL", part, name, ok ? "" : why + "  want=" + want);
        if (ok) pass++;
    }
    static void check(String part, String name, boolean ok, String detail) {
        total++;
        System.out.printf("[%s] %-6s %-34s %s%n", ok ? "PASS" : "FAIL", part, name, ok ? "" : detail);
        if (ok) pass++;
    }
    static Set<String> set(String... xs) { return new HashSet<>(Arrays.asList(xs)); }

    public static void main(String[] args) {
        // ---- common tables ----
        Table r = new Table("r", "a fname lname", "Integer String String", "a");
        r.add(new Comparable[]{1,"Ann","Lee"}); 
        r.add(new Comparable[]{2,"Bob","Ng"});
        r.add(new Comparable[]{3,"Cy","Ito"});  
        r.add(new Comparable[]{4,"Di","Roe"});

        Table s = new Table("s", "b id dept", "Integer Integer String", "b");
        s.add(new Comparable[]{1,5,"CS"}); 
        s.add(new Comparable[]{2,7,"Math"});
        s.add(new Comparable[]{3,5,"Bio"}); 
        s.add(new Comparable[]{4,9,"Econ"});

        // ===== Part A : indexed sel(KeyType) =====
        try { check("A", "sel(KeyType) point lookup", s.sel(new KeyType(3)), set("[3, 5, Bio]")); }
        catch (Throwable t) { check("A", "sel(KeyType) point lookup", false, "threw " + t); }

        // ===== Part A : indexed natural join(Table) =====
        try {
            Table t1 = new Table("t1","id name","Integer String","id");
            t1.add(new Comparable[]{1,"Ann"}); t1.add(new Comparable[]{2,"Bob"}); t1.add(new Comparable[]{3,"Cy"});
            Table t2 = new Table("t2","id dept","Integer String","id");
            t2.add(new Comparable[]{1,"CS"}); t2.add(new Comparable[]{3,"Bio"}); t2.add(new Comparable[]{5,"Econ"});
            check("A", "join(Table) natural join", t1.join(t2), set("[1, Ann, CS]","[3, Cy, Bio]"));
        } catch (Throwable t) { check("A", "join(Table) natural join", false, "threw " + t); }

        // ===== Part A : range query via TreeMap =====
        try { check("A", "range(KeyType,KeyType) query",
                    s.range(new KeyType(2), new KeyType(3)),
                    set("[2, 7, Math]","[3, 5, Bio]")); }
        catch (Throwable t) { check("A", "range query", false, "threw " + t); }

        // ===== Part B : proj eliminates duplicates =====
        try {
            Table p = new Table("p","k v","Integer String","k");
            p.add(new Comparable[]{1,"X"}); p.add(new Comparable[]{2,"X"}); p.add(new Comparable[]{3,"Y"});
            check("B", "proj removes duplicates", p.proj("v"), set("[X]","[Y]"));
        } catch (Throwable t) { check("B", "proj removes duplicates", false, "threw " + t); }

        // ===== Part B : union eliminates duplicates =====
        try {
            Table u1 = new Table("u1","id city","Integer String","id");
            u1.add(new Comparable[]{1,"A"}); u1.add(new Comparable[]{2,"B"});
            Table u2 = new Table("u2","id city","Integer String","id");
            u2.add(new Comparable[]{2,"B"}); u2.add(new Comparable[]{3,"C"});
            check("B", "union removes duplicates", u1.union(u2), set("[1, A]","[2, B]","[3, C]"));
        } catch (Throwable t) { check("B", "union removes duplicates", false, "threw " + t); }

        // ===== Part D : eval the sample query =====
        QueryEngine db = new QueryEngine(); db.put("r", r); db.put("s", s);
        String q = "proj ( [ fname , lname ] , sel ( [ id = 5 ] , join ( [ r.a = s.b ] , r , s ) ) )";
        Node root = RAParser.parse(q);
        try { check("D", "eval sample query", db.eval(root), set("[Ann, Lee]","[Cy, Ito]")); }
        catch (Throwable t) { check("D", "eval sample query", false, "threw " + t); }

        // ===== Part D : eval a minus query =====
        try {
            Table m1 = new Table("m1","id city","Integer String","id");
            m1.add(new Comparable[]{1,"A"}); m1.add(new Comparable[]{2,"B"}); m1.add(new Comparable[]{3,"C"});
            Table m2 = new Table("m2","id city","Integer String","id");
            m2.add(new Comparable[]{2,"B"}); m2.add(new Comparable[]{3,"C"});
            db.put("m1", m1); db.put("m2", m2);
            check("D", "eval minus query", db.eval(RAParser.parse("minus ( m1 , m2 )")), set("[1, A]"));
        } catch (Throwable t) { check("D", "eval minus query", false, "threw " + t); }

        // ===== Part E : optimize pushes sel below the join (structure) =====
        try {
            Node opt = db.optimize(root);
            boolean moved = opt != null && "proj".equals(opt.op)
                    && !opt.children.isEmpty() && "join".equals(opt.children.get(0).op);
            check("E", "optimize pushes sel down", moved,
                  "expected proj -> join at the top (sel moved below the join)");
        } catch (Throwable t) { check("E", "optimize pushes sel down", false, "threw " + t); }

        // ===== Part E : optimized tree gives the SAME answer =====
        try {
            Set<String> before = rows(db.eval(root));
            Set<String> after  = rows(db.eval(db.optimize(root)));
            check("E", "optimized result unchanged", after != null && after.equals(before),
                  "optimized answer differs from original");
        } catch (Throwable t) { check("E", "optimized result unchanged", false, "threw " + t); }

        // ---- informational: speedup (not graded) ----
        try {
            Table big = new Table("big","k v","Integer Integer","k");
            for (int i = 0; i < 10000; i++) big.add(new Comparable[]{i, i});
            Table.comparisons = 0; big.sel("k == 7777");          long lin = Table.comparisons;
            Table.comparisons = 0; big.sel(new KeyType(7777));    long idx = Table.comparisons;
            System.out.printf("%n(info) speedup on 10,000 rows : linear sel: %d comparisons, indexed sel: %d%n", lin, idx);
        } catch (Throwable ignore) {}

        System.out.printf("%n================  SCORE: %d / %d checks passed  ================%n", pass, total);
    }
}
