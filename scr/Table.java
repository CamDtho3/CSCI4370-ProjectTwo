// Table.java  :  CSCI 4370 Project 2  (your relational table)
// =====================================================================================
// The Project-1 operators are written for you and WORK. You add:
//   Part A: sel(KeyType) and join(Table)   -> use the index (fast), don't scan (slow)
//   Part B: proj and union                 -> drop duplicate rows (results are SETS)

// Implement TreeMap interchangeably so that students can run whenevr

//
// USEFUL LINKS
//   Map (index): get/put  https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html
//   HashMap               https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html
//   TreeMap (sorted)      https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html
//   HashSet (dedup)       https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html
// ============================================================================================
package relation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map;

public class Table {

    private String   name;
    private String[] attribute;                 // column names,  e.g. {"a","fname","lname"}
    private Class[]  domain;                     // column types,  e.g. {Integer, String, String}
    private String[] key;                        // primary-key column(s)
    private List<Comparable[]> tuples = new ArrayList<>();          // the rows
    
    // This boolean controls which indexing method is used
    // false: indexing with TreeMap 
    // true: indexing with HashMap
    private boolean mode = false;               

    // PART A : the index maps a key value to its row, so a lookup is instant.
    // We use a TreeMap: it keeps keys SORTED, so it supports exact lookups (get) AND range
    // queries (subMap). A Map also holds each key once, so it eliminates duplicates for free
    // (that is why proj/union below need no HashSet). Swap to new HashMap<>() for O(1) exact
    // lookups if you don't need range().

    // index adjusted to account for mode (TreeMap, HashMap)
    private Map<KeyType, List<Comparable[]>> index = mode ? new HashMap<>() : new TreeMap<>();

    // Counts tuple comparisons, so you can PROVE the speedup in Part A.
    public static long comparisons = 0;

    // ----------------------------------------------------------- constructors
    public Table(String name, String attributes, String domains, String keyStr) {
        this.name      = name;
        this.attribute = attributes.split(" ");
        this.domain    = makeDomains(domains.split(" "));
        this.key       = keyStr.split(" ");
    }
    private Table(String name, String[] attribute, Class[] domain, String[] key) {
        this.name = name; this.attribute = attribute; this.domain = domain; this.key = key;
    }

    // ----------------------------------------------------------- add a row
    /** Add a row (and record it in the index, so lookups stay correct). */
    public Table add(Comparable[] row) {
        tuples.add(row);
        index.computeIfAbsent(keyOf(row), k -> new ArrayList<>()).add(row);
        return this;
    }

    // =================================================================================
    //  PART A  : YOU IMPLEMENT.  Use the index; do NOT loop over all rows.
    // =================================================================================

    /** Fast select by key: return the ONE row whose key equals keyValue (or none).
     *
     *  WHY: the given sel(String) below scans every row (see comparisons++ inside it).
     *       With the index you answer in a single lookup instead.
     *
     *  HOW:
     *    1) comparisons++;                              // we do exactly one probe
     *    2) Comparable[] row = index.get(keyValue);     // Map.get returns null if absent
     *    3) if (row != null) rows.add(row);
     *  (Map.get: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#get(java.lang.Object) )
     */
    public Table sel(KeyType keyValue) {
        List<Comparable[]> rows = new ArrayList<>();

        // TODO Part A (3 lines) : see HOW above.
        comparisons++;
        List<Comparable[]> bucket = index.get(keyValue);
        if (bucket != null) rows.addAll(bucket);


        return new Table(name, attribute, domain, key).addAll(rows);
    }

    /** A2. Fast RANGE lookup: return every row whose key is between lo and hi (inclusive).
     *  This is why a TreeMap is worth using : a HashMap can only do exact lookups, not ranges.
     *  HINT: for (Comparable[] row : ((NavigableMap<KeyType,Comparable[]>) index)
     *                                   .subMap(lo, true, hi, true).values()) rows.add(row);
     */
    public Table range(KeyType lo, KeyType hi) {
        List<Comparable[]> rows = new ArrayList<>();
        // TODO Part A (range): use index.subMap(lo, true, hi, true).values(); see HINT above.
        if (!mode) {
            for (List<Comparable[]> bucket : ((NavigableMap<KeyType, List<Comparable[]>>) index)
                    .subMap(lo, true, hi, true).values()) {
                rows.addAll(bucket);
}
            return new Table(name, attribute, domain, key).addAll(rows);
        } else {
            throw new UnsupportedOperationException("range() requires mode = 0 (TreeMap)");
        } 
    }

    /** Fast natural join: match rows of THIS table to rows of table2 that share the
     *  common column value, by PROBING table2's index (not comparing every pair).
     *
     *  WHY: the given equi-join below is a nested loop (n * m comparisons). Probing the
     *       index makes it n lookups.
     *
     *  HOW (sketch):
     *    1) find the column name(s) both tables share (the join key).
     *    2) for each row in THIS table:
     *         - build the key value from the shared column(s);
     *         - comparisons++;  Comparable[] match = table2.index.get(thatKey);
     *         - if match != null, glue the two rows together (keep table2's non-shared columns).
     *    3) return the combined table.
     *  Hint: look at how the given equi-join builds result attributes/rows for the gluing part.
     */
    public Table join(Table table2) {

        // TODO Part A : probe table2.index instead of a nested loop.

        int[] thisPos = new int[table2.key.length];
        for(int i = 0; i < table2.key.length; i++) {
            thisPos[i] = columnIndex(table2.key[i]);
        }

        List<String> attributeList = new ArrayList<>(Arrays.asList(attribute));
        List<Class> domainList = new ArrayList<>(Arrays.asList(domain));
        List<Integer> table2KeepPos = new ArrayList<>();
        for (int i = 0; i < table2.attribute.length; i++) {
            if (!containsAll(new String[]{table2.attribute[i]}, table2.key)) { 
                attributeList.add(table2.attribute[i]);
                domainList.add(table2.domain[i]);
                table2KeepPos.add(i);
            }
        }

        String[] newAttribute = attributeList.toArray(new String[0]);
        Class[] newDomain = domainList.toArray(new Class[0]);

        List<Comparable[]> rows = new ArrayList<>();
        for (Comparable[] row : tuples) {
            Comparable[] keyVals = new Comparable[thisPos.length];
            for (int i = 0; i < thisPos.length; i++) keyVals[i] = row[thisPos[i]];
            KeyType thatKey = new KeyType(keyVals);

            comparisons++;
            List<Comparable[]> matches = table2.index.get(thatKey);
            if (matches != null) {
                for (Comparable[] match : matches) {        // one probe row can now match MANY
                    Comparable[] newRow = new Comparable[newAttribute.length];
                    System.arraycopy(row, 0, newRow, 0, row.length);
                    for (int i = 0; i < table2KeepPos.size(); i++) {
                        newRow[row.length + i] = match[table2KeepPos.get(i)];
                    }
                    rows.add(newRow);
                    }
                }
        }

        return new Table(name, newAttribute, newDomain, key).addAll(rows);
    }

    // =================================================================================
    //  PART B  : YOU IMPLEMENT.  Remove duplicate rows (RA results are SETS).
    //  Trick: keep a HashSet<KeyType> of rows already added; add a row only if it's new.
    //  (HashSet.add returns false if the item was already present : very handy here.)
    //  HashSet: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html
    //  IMPORTANT: wrap each row in KeyType before putting it in the set. A raw
    //  Comparable[] compares by reference, so two equal rows would look "different"
    //  and the duplicate would slip through.
    // =================================================================================

    /** Keep only the named columns. (Right now it KEEPS duplicates : make it drop them.) */
     public Table proj(String attrs) {
        String[] cols = attrs.split(" ");
        int[]    pos  = columnIndexes(cols);
        String[] newKey = containsAll(cols, key) ? key : cols;   // key must exist in the result
        // Duplicate elimination is AUTOMATIC: a Map holds each key once, so putting every
        // projected row into a Map (keyed by the whole row) drops repeats for free.
        HashSet<KeyType> found = new HashSet<>();
        List<Comparable[]> rows = new ArrayList<>();
        for (Comparable[] row : tuples) {
            Comparable[] newRow = new Comparable[cols.length];
            for (int i = 0; i < cols.length; i++) newRow[i] = row[pos[i]];
            KeyType keyType = new KeyType(newRow);
            if (found.add(keyType)) {
                rows.add(newRow);
            }
        }
        return new Table(name, cols, domainsOf(cols), newKey).addAll(rows);
    }

    /** Combine the rows of both tables. (Right now it KEEPS duplicates : make it drop them.) */
    public Table union(Table table2) {
        if (!sameShape(table2)) return null;
        // Duplicate elimination is AUTOMATIC: a Map keyed by the whole row keeps each row once.
         HashSet<KeyType> found = new HashSet<>();
         List<Comparable[]> rows = new ArrayList<>();
        for (Comparable[] row : tuples) {        
            KeyType rowKey = new KeyType(row);

        if (found.add(rowKey)) {
            rows.add(row);
        }
        }
   for (Comparable[] row : table2.tuples) {
        KeyType rowKey = new KeyType(row);

        if (found.add(rowKey)) {
            rows.add(row);
        }
    }
        return new Table(name, attribute, domain, key).addAll(rows);
    }

    // =================================================================================
    //  Project-1 operators : GIVEN, already working. (Read sel(String) to see how the
    //  comparison counter is used, and how a condition string is taken apart.)
    // =================================================================================

    /** Select rows matching a condition like  "id == 5"  or  "city == 'Athens'". */
    public Table sel(String condition) {
        String[] part = condition.split(" ");                // 3 tokens: col op value
        String col = part[0], op = part[1];
        Comparable value = makeValue(col, part[2]);
        int c = columnIndex(col);
        List<Comparable[]> rows = new ArrayList<>();
        for (Comparable[] row : tuples) {
            comparisons++;                                   // counts each row we check (a full scan)
            if (compare(row[c], op, value)) rows.add(row);
        }
        return new Table(name, attribute, domain, key).addAll(rows);
    }

    /** Rows that are in this table but NOT in table2. */
    public Table minus(Table table2) {
        if (!sameShape(table2)) return null;
        HashSet<KeyType> inOther = new HashSet<>();
        for (Comparable[] row : table2.tuples) inOther.add(new KeyType(row));
        List<Comparable[]> rows = new ArrayList<>();
        for (Comparable[] row : tuples)
            if (!inOther.contains(new KeyType(row))) rows.add(row);
        return new Table(name, attribute, domain, key).addAll(rows);
    }

    /** Equi-join: keep pairs of rows where  this.aThis  equals  table2.aThat.
     *  (This is the nested-loop version, a good model for the gluing in Part A's join.) */
    public Table join(String aThis, String aThat, Table table2) {
        int c1 = columnIndex(aThis);
        int c2 = table2.columnIndex(aThat);
        String[] newAttr = joinNames(attribute, table2.attribute);
        Class[]  newDom  = concat(domain, table2.domain);
        List<Comparable[]> rows = new ArrayList<>();
        for (Comparable[] t1 : tuples)
            for (Comparable[] t2 : table2.tuples) {
                comparisons++;                               // counts each pair (n * m)
                if (t1[c1].equals(t2[c2])) rows.add(concat(t1, t2));
            }
        return new Table(name, newAttr, newDom, key).addAll(rows);
    }

    /** Print the table with its rows. */
    public void show() {
        System.out.println("\nTable " + name + ":  " + String.join("  ", attribute));
        for (Comparable[] row : tuples) System.out.println("   " + Arrays.toString(row));
        System.out.println("   (" + tuples.size() + " rows)");
    }

    /** The column names of this table (you'll need this for Part E). */
    public String[] columns() { return attribute; }

    /** How many rows the table has. */
    public int rowCount() { return tuples.size(); }

    // =================================================================================
    //  small helpers - GIVEN, no need to change
    // =================================================================================
    private Table addAll(List<Comparable[]> rows) { for (Comparable[] r : rows) add(r); return this; }

    private KeyType keyOf(Comparable[] row) {
        Comparable[] kv = new Comparable[key.length];
        for (int i = 0; i < key.length; i++) kv[i] = row[columnIndex(key[i])];
        return new KeyType(kv);
    }
    private int columnIndex(String col) {
        for (int i = 0; i < attribute.length; i++) if (attribute[i].equals(col)) return i;
        throw new RuntimeException("no such column: " + col);
    }
    private int[] columnIndexes(String[] cols) {
        int[] r = new int[cols.length];
        for (int i = 0; i < cols.length; i++) r[i] = columnIndex(cols[i]);
        return r;
    }
    private Class[] domainsOf(String[] cols) {
        Class[] d = new Class[cols.length];
        for (int i = 0; i < cols.length; i++) d[i] = domain[columnIndex(cols[i])];
        return d;
    }
    private boolean containsAll(String[] cols, String[] needed) {
        for (String k : needed) {
            boolean found = false;
            for (String c : cols) if (c.equals(k)) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }
    private boolean sameShape(Table t2) {
        if (domain.length != t2.domain.length) return false;
        for (int i = 0; i < domain.length; i++) if (domain[i] != t2.domain[i]) return false;
        return true;
    }
    private boolean compare(Comparable left, String op, Comparable right) {
        int c = left.compareTo(right);
        if (op.equals("=="))  return c == 0;
        if (op.equals("!="))  return c != 0;
        if (op.equals("<"))   return c <  0;
        if (op.equals("<="))  return c <= 0;
        if (op.equals(">"))   return c >  0;
        if (op.equals(">="))  return c >= 0;
        throw new RuntimeException("bad operator: " + op);
    }
    private Comparable makeValue(String col, String token) {
        if (token.startsWith("'") && token.endsWith("'")) return token.substring(1, token.length() - 1);
        if (domain[columnIndex(col)] == Integer.class) return Integer.valueOf(token);
        return token;
    }
    private static String[] joinNames(String[] a, String[] b) {     // rename a collision to name+"2"
        HashSet<String> seen = new HashSet<>(Arrays.asList(a));
        String[] out = new String[a.length + b.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i];
        for (int j = 0; j < b.length; j++) out[a.length + j] = seen.contains(b[j]) ? b[j] + "2" : b[j];
        return out;
    }
    private static Comparable[] concat(Comparable[] a, Comparable[] b) {
        Comparable[] r = new Comparable[a.length + b.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i];
        for (int j = 0; j < b.length; j++) r[a.length + j] = b[j];
        return r;
    }
    private static Class[] concat(Class[] a, Class[] b) {
        Class[] r = new Class[a.length + b.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i];
        for (int j = 0; j < b.length; j++) r[a.length + j] = b[j];
        return r;
    }
    private static Class[] makeDomains(String[] names) {
        Class[] c = new Class[names.length];
        for (int i = 0; i < names.length; i++) c[i] = names[i].equals("Integer") ? Integer.class : String.class;
        return c;
    }
}