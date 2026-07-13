// RAParser.java  -  PROVIDED. You do NOT need to read or edit this file.
// It turns a query string into a Node tree, prints a tree, and has a few
// tiny string helpers that eval() uses.
package relation;

import java.util.ArrayList;
import java.util.List;

public class RAParser {

    // ---- helpers used by eval() so you don't fight the text ----
    public static String condition(String arg) { return arg.replace(" = ", " == "); }   // id = 5  ->  id == 5
    public static String attrs(String arg)     { return arg.replace(",", " ").replaceAll("\\s+", " ").trim(); } // fname , lname -> fname lname
    public static String joinLeft(String arg)  { return strip(arg.split(" ")[0]); }      // r.a = s.b  ->  a
    public static String joinRight(String arg) { return strip(arg.split(" ")[2]); }      // r.a = s.b  ->  b
    private static String strip(String q)      { int d = q.indexOf('.'); return d < 0 ? q : q.substring(d + 1); }

    // ---- parse a blank-separated query into a Node tree ----
    private static List<String> toks; private static int pos;
    public static Node parse(String query) {
        toks = new ArrayList<>();
        for (String s : query.trim().split("\\s+")) if (!s.isEmpty()) toks.add(s);
        pos = 0;
        return expr();
    }
    private static String peek()  { return pos < toks.size() ? toks.get(pos) : null; }
    private static String next()  { return toks.get(pos++); }
    private static void eat(String s){ String g = next(); if (!g.equals(s)) throw new RuntimeException("parse error: expected " + s + " got " + g); }

    private static Node expr() {
        String t = peek();
        if (t.equals("proj") || t.equals("sel"))   return unary(next());
        if (t.equals("join"))                       return joinExpr();
        if (t.equals("union") || t.equals("minus")) return setOp(next());
        Node leaf = new Node(); leaf.table = next(); return leaf;        // table name
    }
    private static Node unary(String op) {
        eat("("); String a = bracket(); eat(",");
        Node child = expr(); eat(")");
        Node n = new Node(); n.op = op; n.arg = a; n.children.add(child); return n;
    }
    private static Node joinExpr() {
        next(); eat("("); String a = bracket(); eat(",");
        Node l = expr(); eat(","); Node r = expr(); eat(")");
        Node n = new Node(); n.op = "join"; n.arg = a; n.children.add(l); n.children.add(r); return n;
    }
    private static Node setOp(String op) {
        eat("("); Node l = expr(); eat(","); Node r = expr(); eat(")");
        Node n = new Node(); n.op = op; n.children.add(l); n.children.add(r); return n;
    }
    private static String bracket() {
        eat("[");
        StringBuilder sb = new StringBuilder();
        while (peek() != null && !peek().equals("]")) { if (sb.length() > 0) sb.append(' '); sb.append(next()); }
        eat("]");
        return sb.toString();
    }

    // ---- print the tree ----
    public static void printTree(Node n, String prefix, String connector) {
        System.out.println(prefix + connector + n.label());
        String childPrefix = prefix + (connector.equals("\u2514\u2500\u2500 ") ? "    " : "\u2502   ");
        for (int i = 0; i < n.children.size(); i++) {
            boolean last = (i == n.children.size() - 1);
            printTree(n.children.get(i), childPrefix, last ? "\u2514\u2500\u2500 " : "\u251c\u2500\u2500 ");
        }
    }
}
