"""Regenerate Venus Notebooks tutorial files with dual JShell + Java mode examples."""
import json, os

base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'notebooks')

def cell(cid, ctype, source, anchor=None):
    return {
        "id": cid, "type": ctype, "mode": "jshell",
        "source": source, "output": "", "returnValue": None,
        "executed": False, "executionCount": None, "error": "",
        "anchor": anchor, "dependsOn": [], "pipelineSteps": []
    }

def md(cid, source):
    return cell(cid, "MARKDOWN", source)

def code(cid, source, anchor=None):
    return cell(cid, "CODE", source, anchor)

# ─────────────────────────────────────────────────────────────
# JAVA 101
# ─────────────────────────────────────────────────────────────
java101 = {
    "id": "java-101",
    "name": "Java 101 \u2014 Fundamentals",
    "description": "Core Java fundamentals for beginners",
    "created": "2026-03-14T10:00:00",
    "modified": "2026-03-14T00:00:00",
    "cells": [
        md("j101-intro", """\
# Java 101 \u2014 Fundamentals

Welcome to **Java 101**! This notebook covers core Java fundamentals from scratch.

Run each cell with **Shift+Enter** or click \u25b6. Variables from earlier cells stay in scope for later cells \u2014 just like a real Java REPL.

> **Level:** Beginner \u00b7 **Time:** ~25 minutes"""),

        md("j101-modes", """\
## \ud83d\udca1 JShell Mode vs Java Mode

This notebook shows **both** approaches side-by-side.

| | JShell Mode (default) | Java Mode (full program) |
|---|---|---|
| **What it is** | Interactive REPL \u2014 like Python | Full Java class with `main()` |
| **When to use** | Quick experiments, exploring APIs | Production code, learning class structure |
| **Class wrapper** | Not required | Required (`public class Foo { ... }`) |
| **Run** | Each cell runs immediately | Write full class, then run |
| **Cell label** | \ud83d\udd35 **JShell** prefix in comments | \u2615 **Java** prefix in comments |

> Both work in Venus Notebooks! JShell cells run top-to-bottom sharing state. Java cells are self-contained."""),

        md("j101-h2-hello", "## 1. Hello World & Output"),

        code("j101-hello-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 no class wrapper needed, runs line by line
System.out.println("Hello, Venus Notebooks!");
System.out.println("Java version: " + Runtime.version());

// printf for formatted output
System.out.printf("Today is %s%n", java.time.LocalDate.now());

var greeting = String.format("Welcome to Java %s!", Runtime.version().version().get(0));
System.out.println(greeting);""", "hello"),

        code("j101-hello-java", """\
// \u2615 Java Mode \u2014 full class with main() method (standard Java program structure)
// In a real .java file this would be the entire file content.
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, Venus Notebooks!");
        System.out.println("Java version: " + Runtime.version());
        System.out.printf("Today is %s%n", java.time.LocalDate.now());
        String greeting = String.format("Welcome to Java %s!",
                Runtime.version().version().get(0));
        System.out.println(greeting);
    }
}
// JShell can define and run classes too:
new HelloWorld().main(new String[]{});  // call main explicitly"""),

        md("j101-h2-vars", "## 2. Variables & Types"),

        code("j101-vars-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 declare variables at top level, 'var' infers type
var name    = "Alice";      // String
var age     = 30;           // int
var height  = 1.75;         // double
var active  = true;         // boolean
var initial = 'A';          // char

// Explicit types also work
String city = "New York";
int year    = 2026;

System.out.println("Name:   " + name   + " (String)");
System.out.println("Age:    " + age    + " (int)");
System.out.println("Height: " + height + " (double)");
System.out.println("City:   " + city   + ", Year: " + year);""", "variables"),

        code("j101-vars-java", """\
// \u2615 Java Mode \u2014 variables inside a method (same syntax, same types)
public class VariablesDemo {
    public static void main(String[] args) {
        // Explicit types (preferred in production code for clarity)
        String name    = "Alice";
        int    age     = 30;
        double height  = 1.75;
        boolean active = true;
        char initial   = 'A';

        // var also works inside methods (Java 10+)
        var city = "New York";
        var year = 2026;

        System.out.printf("%-8s %-10s %s%n", "name",   name,   "(String)");
        System.out.printf("%-8s %-10d %s%n", "age",    age,    "(int)");
        System.out.printf("%-8s %-10.2f %s%n","height", height, "(double)");
        System.out.printf("%-8s %-10b %s%n", "active", active, "(boolean)");
    }
}
new VariablesDemo().main(null);"""),

        md("j101-h2-control", "## 3. Control Flow"),

        code("j101-control-jshell", """\
// \ud83d\udd35 JShell Mode
int score = 85;

// Traditional if-else
if (score >= 90)      System.out.println("Grade: A");
else if (score >= 80) System.out.println("Grade: B");
else if (score >= 70) System.out.println("Grade: C");
else                  System.out.println("Grade: F");

// Switch expression (Java 14+)
var grade = switch (score / 10) {
    case 10, 9 -> "A";
    case 8     -> "B";
    case 7     -> "C";
    case 6     -> "D";
    default    -> "F";
};
System.out.println("Switch grade: " + grade);

// Ternary
System.out.println("Result: " + (score >= 60 ? "Pass" : "Fail"));""", "control-flow"),

        code("j101-control-java", """\
// \u2615 Java Mode \u2014 same control flow inside a class
public class ControlFlowDemo {
    static String getGrade(int score) {
        return switch (score / 10) {
            case 10, 9 -> "A";
            case 8     -> "B";
            case 7     -> "C";
            case 6     -> "D";
            default    -> "F";
        };
    }

    public static void main(String[] args) {
        int[] scores = {95, 85, 75, 65, 55};
        System.out.println("Score | Grade | Result");
        System.out.println("------|-------|-------");
        for (int score : scores) {
            System.out.printf("  %3d | %5s | %s%n",
                score, getGrade(score), score >= 60 ? "Pass" : "Fail");
        }
    }
}
new ControlFlowDemo().main(null);"""),

        md("j101-h2-loops", "## 4. Loops"),

        code("j101-loops-jshell", """\
// \ud83d\udd35 JShell Mode
// Classic for loop
System.out.print("1\u20135:  ");
for (int i = 1; i <= 5; i++) System.out.print(i + " ");
System.out.println();

// While loop
int n = 1;
System.out.print("Powers of 2: ");
while (n <= 32) { System.out.print(n + " "); n *= 2; }
System.out.println();

// Enhanced for-each
var fruits = new String[]{"apple", "banana", "cherry"};
for (var fruit : fruits) System.out.println("  \u2022 " + fruit);

// break / continue
System.out.print("Odd 1\u20139: ");
for (int i = 1; i <= 10; i++) {
    if (i % 2 == 0) continue;
    if (i > 9) break;
    System.out.print(i + " ");
}
System.out.println();""", "loops"),

        code("j101-loops-java", """\
// \u2615 Java Mode \u2014 loops inside a class
import java.util.List;

public class LoopsDemo {
    // do-while (less common; useful when body runs at least once)
    static void countDown(int from) {
        System.out.print("Countdown: ");
        do {
            System.out.print(from + " ");
            from--;
        } while (from > 0);
        System.out.println("Go!");
    }

    public static void main(String[] args) {
        // Nested loops \u2014 multiplication table
        System.out.println("Multiplication table (1\u20134):");
        for (int i = 1; i <= 4; i++) {
            for (int j = 1; j <= 4; j++) {
                System.out.printf("%3d", i * j);
            }
            System.out.println();
        }
        countDown(5);

        // for-each over a List
        var colors = List.of("red", "green", "blue");
        colors.forEach(c -> System.out.println("  Color: " + c));
    }
}
new LoopsDemo().main(null);"""),

        md("j101-h2-methods", "## 5. Methods (Functions)"),

        code("j101-methods-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 define methods at top level (no class needed)
int add(int x, int y) { return x + y; }

double circleArea(double r) { return Math.PI * r * r; }

boolean isPrime(int n) {
    if (n < 2) return false;
    for (int i = 2; i <= Math.sqrt(n); i++)
        if (n % i == 0) return false;
    return true;
}

System.out.println("add(3,7) = " + add(3, 7));
System.out.printf("circleArea(5) = %.4f%n", circleArea(5.0));

var primes = new java.util.ArrayList<Integer>();
for (int i = 2; i <= 30; i++) if (isPrime(i)) primes.add(i);
System.out.println("Primes \u2264 30: " + primes);""", "methods"),

        code("j101-methods-java", """\
// \u2615 Java Mode \u2014 methods inside a class, with overloading
public class MethodsDemo {
    // Method overloading: same name, different parameter types
    static String greet(String name) {
        return "Hello, " + name + "!";
    }
    static String greet(String name, String title) {
        return "Hello, " + title + " " + name + "!";
    }

    // Varargs: accepts any number of ints
    static int sum(int... nums) {
        int total = 0;
        for (int n : nums) total += n;
        return total;
    }

    // Recursive method
    static long factorial(int n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }

    public static void main(String[] args) {
        System.out.println(greet("Alice"));
        System.out.println(greet("Smith", "Dr."));
        System.out.println("sum(1..5) = " + sum(1, 2, 3, 4, 5));
        for (int i = 0; i <= 7; i++)
            System.out.printf("%d! = %d%n", i, factorial(i));
    }
}
new MethodsDemo().main(null);"""),

        md("j101-h2-classes", "## 6. Classes & Objects"),

        code("j101-classes-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 define a class inline, then instantiate it
class Point {
    double x, y;

    Point(double x, double y) { this.x = x; this.y = y; }

    double distanceTo(Point other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx*dx + dy*dy);
    }

    @Override public String toString() {
        return String.format("Point(%.1f, %.1f)", x, y);
    }
}

var p1 = new Point(0, 0);
var p2 = new Point(3, 4);
System.out.println("p1 = " + p1);
System.out.println("p2 = " + p2);
System.out.printf("Distance p1\u2192p2 = %.2f%n", p1.distanceTo(p2));""", "classes"),

        code("j101-classes-java", """\
// \u2615 Java Mode \u2014 classic OOP: class with encapsulation, constructor, toString
public class BankAccount {
    private String owner;
    private double balance;

    public BankAccount(String owner, double initialBalance) {
        this.owner   = owner;
        this.balance = initialBalance;
    }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        balance += amount;
        System.out.printf("  Deposited $%.2f \u2192 balance $%.2f%n", amount, balance);
    }

    public void withdraw(double amount) {
        if (amount > balance) throw new IllegalStateException("Insufficient funds");
        balance -= amount;
        System.out.printf("  Withdrew $%.2f \u2192 balance $%.2f%n", amount, balance);
    }

    @Override public String toString() {
        return String.format("BankAccount[owner=%s, balance=$%.2f]", owner, balance);
    }
}

var account = new BankAccount("Alice", 1000.00);
System.out.println("Created: " + account);
account.deposit(500);
account.withdraw(200);
System.out.println("Final:   " + account);"""),

        md("j101-footer", """\
---

## You've completed Java 101!

**Next steps:**
| Notebook | Topics |
|----------|--------|
| **Java 202** | Records, Streams, Lambdas, Optional, Modern Java |
| **Java 302** | Concurrency, Pattern Matching, Advanced Patterns |

Open a notebook from the selector above, or click **Browse** to find it.""")
    ],
    "metadata": {},
    "sessionId": None,
    "filename": "java-101.vnb"
}

# ─────────────────────────────────────────────────────────────
# JAVA 202
# ─────────────────────────────────────────────────────────────
java202 = {
    "id": "java-202",
    "name": "Java 202 \u2014 Modern Java & OOP",
    "description": "Records, Streams, Lambdas, Optional, Generics",
    "created": "2026-03-14T10:00:00",
    "modified": "2026-03-14T00:00:00",
    "cells": [
        md("j202-intro", """\
# Java 202 \u2014 Modern Java & OOP

This notebook covers **modern Java** features introduced in Java 14\u201321.

> **Level:** Intermediate \u00b7 **Prerequisites:** Java 101 \u00b7 **Time:** ~30 minutes

Each section shows the same concept in both **JShell mode** and **Java mode**:
- \ud83d\udd35 **JShell** \u2014 quick, interactive, no boilerplate
- \u2615 **Java** \u2014 production-style code with full class structure"""),

        md("j202-h2-records", "## 1. Records (Java 16+)"),

        code("j202-records-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 records are compact, immutable data classes
record Person(String name, int age) {
    // Custom method on a record
    String greeting() { return "Hi, I'm " + name + " and I'm " + age; }
}

record Point(double x, double y) {
    // Computed property
    double magnitude() { return Math.sqrt(x*x + y*y); }
}

var alice = new Person("Alice", 30);
var bob   = new Person("Bob",   25);

System.out.println(alice);               // auto-generated toString
System.out.println(alice.name());        // auto-generated accessor
System.out.println(alice.greeting());
System.out.println(alice.equals(bob));   // auto-generated equals

var p = new Point(3.0, 4.0);
System.out.printf("Point %s magnitude = %.2f%n", p, p.magnitude());""", "records"),

        code("j202-records-java", """\
// \u2615 Java Mode \u2014 records in production code
import java.util.List;

public record Product(String id, String name, double price) {
    // Compact constructor: validates on creation
    public Product {
        if (price < 0) throw new IllegalArgumentException("Price cannot be negative");
        name = name.strip();  // normalise name
    }

    // Static factory method
    public static Product free(String id, String name) {
        return new Product(id, name, 0.0);
    }

    // Instance helper
    public Product withDiscount(double pct) {
        return new Product(id, name, price * (1 - pct / 100));
    }
}

// Demonstrate:
var laptop  = new Product("P001", "  Laptop  ", 999.99);
var freebie = Product.free("P002", "Sticker");
var sale    = laptop.withDiscount(20);

List.of(laptop, freebie, sale).forEach(System.out::println);"""),

        md("j202-h2-lambdas", "## 2. Lambdas & Functional Interfaces"),

        code("j202-lambdas-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 lambdas as first-class functions
import java.util.function.*;

// Lambda syntax: (params) -> expression  or  (params) -> { block }
Function<String, Integer> length = s -> s.length();
Predicate<Integer>  isEven = n -> n % 2 == 0;
Consumer<String>    print  = System.out::println;  // method reference
Supplier<String>    now    = () -> java.time.LocalTime.now().toString();

System.out.println(length.apply("Hello"));
System.out.println(isEven.test(42));
print.accept("Method reference!");
System.out.println("Time: " + now.get());

// BiFunction: two inputs
BiFunction<Integer, Integer, Integer> max = Math::max;
System.out.println("max(3,7) = " + max.apply(3, 7));

// Compose: g(f(x))
var shout = length.andThen(n -> n + " chars").andThen(String::toUpperCase);
System.out.println(shout.apply("hello"));""", "lambdas"),

        code("j202-lambdas-java", """\
// \u2615 Java Mode \u2014 custom functional interface + strategy pattern
import java.util.*;
import java.util.function.*;

@FunctionalInterface
interface Transformer<T> {
    T transform(T input);

    // Default method for chaining
    default Transformer<T> andThen(Transformer<T> next) {
        return input -> next.transform(this.transform(input));
    }
}

public class LambdaDemo {
    public static void main(String[] args) {
        // Build a pipeline of transformers
        Transformer<String> trim  = String::strip;
        Transformer<String> upper = String::toUpperCase;
        Transformer<String> excl  = s -> s + "!";

        var pipeline = trim.andThen(upper).andThen(excl);

        List<String> inputs = List.of("  hello  ", " world ", " venus ");
        inputs.stream()
              .map(pipeline::transform)
              .forEach(System.out::println);
    }
}
new LambdaDemo().main(null);"""),

        md("j202-h2-streams", "## 3. Streams API"),

        code("j202-streams-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 Stream pipeline: source \u2192 intermediate ops \u2192 terminal op
import java.util.*;
import java.util.stream.*;

var numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

// Filter + map + collect
var evenSquares = numbers.stream()
    .filter(n -> n % 2 == 0)
    .map(n -> n * n)
    .collect(Collectors.toList());
System.out.println("Even squares: " + evenSquares);

// reduce: fold a stream to one value
int product = numbers.stream().reduce(1, (a, b) -> a * b);
System.out.println("Product: " + product);

// Statistics
var stats = numbers.stream().mapToInt(Integer::intValue).summaryStatistics();
System.out.printf("Min=%d Max=%d Sum=%d Avg=%.1f%n",
    stats.getMin(), stats.getMax(), (long)stats.getSum(), stats.getAverage());

// Grouping
var words = List.of("apple", "ant", "bat", "bee", "cat", "cherry");
var byLetter = words.stream().collect(Collectors.groupingBy(w -> w.charAt(0)));
System.out.println("Grouped: " + byLetter);""", "streams"),

        code("j202-streams-java", """\
// \u2615 Java Mode \u2014 Streams with records, collectors, and flatMap
import java.util.*;
import java.util.stream.*;

record Student(String name, String subject, int score) {}

public class StreamsDemo {
    public static void main(String[] args) {
        var students = List.of(
            new Student("Alice", "Math",    92),
            new Student("Bob",   "Math",    78),
            new Student("Alice", "Science", 88),
            new Student("Bob",   "Science", 85),
            new Student("Carol", "Math",    95),
            new Student("Carol", "Science", 90)
        );

        // Average score by subject
        System.out.println("Average by subject:");
        students.stream()
            .collect(Collectors.groupingBy(Student::subject,
                     Collectors.averagingInt(Student::score)))
            .forEach((sub, avg) -> System.out.printf("  %-9s %.1f%n", sub, avg));

        // Top student overall
        students.stream()
            .max(Comparator.comparingInt(Student::score))
            .ifPresent(s -> System.out.println("Top student: " + s.name() +
                " (" + s.score() + ")"));

        // All passing scores (>=80) sorted descending
        var passing = students.stream()
            .filter(s -> s.score() >= 80)
            .sorted(Comparator.comparingInt(Student::score).reversed())
            .map(s -> s.name() + "/" + s.subject() + "=" + s.score())
            .collect(Collectors.joining(", "));
        System.out.println("Passing: " + passing);
    }
}
new StreamsDemo().main(null);"""),

        md("j202-h2-optional", "## 4. Optional & Null Safety"),

        code("j202-optional-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 Optional avoids NullPointerException
import java.util.*;

// Creating Optional
var present = Optional.of("Hello");
var empty   = Optional.<String>empty();
var maybe   = Optional.ofNullable(null);  // won't throw

// Checking and extracting
System.out.println("present.isPresent() = " + present.isPresent());
System.out.println("empty.isEmpty()     = " + empty.isEmpty());
System.out.println("present.get()       = " + present.get());

// Safe extraction
System.out.println(empty.orElse("default"));
System.out.println(empty.orElseGet(() -> "computed default"));

// Transforming
var upper = present.map(String::toUpperCase);
System.out.println("Mapped: " + upper.orElse("nothing"));

// ifPresent
present.ifPresent(v -> System.out.println("Value: " + v));
empty.ifPresentOrElse(
    v -> System.out.println("Got: " + v),
    () -> System.out.println("Empty!")
);""", "optional"),

        code("j202-optional-java", """\
// \u2615 Java Mode \u2014 Optional in a real-world lookup scenario
import java.util.*;
import java.util.stream.*;

public class OptionalDemo {
    static final Map<String, String> PHONE_BOOK = Map.of(
        "Alice", "555-0100",
        "Bob",   "555-0200"
    );

    // Returns Optional instead of null
    static Optional<String> findPhone(String name) {
        return Optional.ofNullable(PHONE_BOOK.get(name));
    }

    // Chains Optional operations safely
    static String formatEntry(String name) {
        return findPhone(name)
            .map(phone -> name + ": " + phone)
            .map(String::toUpperCase)
            .orElse(name + ": NOT FOUND");
    }

    public static void main(String[] args) {
        List.of("Alice", "Bob", "Carol", "Dave")
            .stream()
            .map(OptionalDemo::formatEntry)
            .forEach(System.out::println);
    }
}
new OptionalDemo().main(null);"""),

        md("j202-h2-exceptions", "## 5. Exceptions & Error Handling"),

        code("j202-exceptions-jshell", """\
// \ud83d\udd35 JShell Mode
// Checked exceptions must be caught or declared
try {
    int result = 10 / 0;
} catch (ArithmeticException e) {
    System.out.println("Caught: " + e.getMessage());
}

// Multiple catch blocks
String[] data = {"42", "hello", null};
for (var s : data) {
    try {
        int val = Integer.parseInt(s);
        System.out.println("Parsed: " + val);
    } catch (NumberFormatException e) {
        System.out.println("Not a number: " + s);
    } catch (NullPointerException e) {
        System.out.println("Null value!");
    }
}

// Custom exception
class InsufficientFundsException extends RuntimeException {
    InsufficientFundsException(double amount) {
        super("Insufficient funds: need $" + amount);
    }
}

try {
    throw new InsufficientFundsException(50.0);
} catch (InsufficientFundsException e) {
    System.out.println("Error: " + e.getMessage());
}""", "exceptions"),

        code("j202-exceptions-java", """\
// \u2615 Java Mode \u2014 exceptions with try-with-resources
import java.io.*;

public class ExceptionDemo {
    // Checked exception: caller must handle it
    static int divide(int a, int b) throws ArithmeticException {
        if (b == 0) throw new ArithmeticException("Division by zero");
        return a / b;
    }

    // try-with-resources: AutoCloseable closed automatically
    static String readResource(String name) {
        // StringReader implements AutoCloseable
        try (var reader = new StringReader("Data from " + name)) {
            var buf = new char[50];
            int n = reader.read(buf);
            return new String(buf, 0, n);
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        // Multi-catch (Java 7+)
        try {
            System.out.println(divide(10, 2));
            System.out.println(divide(10, 0));
        } catch (ArithmeticException | IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        } finally {
            System.out.println("(finally always runs)");
        }

        System.out.println(readResource("config.txt"));
    }
}
new ExceptionDemo().main(null);"""),

        md("j202-footer", """\
---

## You've completed Java 202!

**Next steps:**
| Notebook | Topics |
|----------|--------|
| **Java 302** | Concurrency, Sealed types, Pattern matching, Virtual threads |""")
    ],
    "metadata": {},
    "sessionId": None,
    "filename": "java-202.vnb"
}

# ─────────────────────────────────────────────────────────────
# JAVA 302
# ─────────────────────────────────────────────────────────────
java302 = {
    "id": "java-302",
    "name": "Java 302 \u2014 Advanced Java",
    "description": "Concurrency, Sealed types, Pattern matching, Virtual threads, Builders",
    "created": "2026-03-14T10:00:00",
    "modified": "2026-03-14T00:00:00",
    "cells": [
        md("j302-intro", """\
# Java 302 \u2014 Advanced Java

Advanced Java features from Java 17\u201321.

> **Level:** Advanced \u00b7 **Prerequisites:** Java 202 \u00b7 **Time:** ~35 minutes

\ud83d\udd35 **JShell** cells \u2014 quick interactive demos
\u2615 **Java** cells \u2014 production-ready patterns"""),

        md("j302-h2-sealed", "## 1. Sealed Classes & Pattern Matching"),

        code("j302-sealed-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 sealed class hierarchy
sealed interface Shape permits Circle, Rectangle, Triangle {}
record Circle(double radius)             implements Shape {}
record Rectangle(double width, double h) implements Shape {}
record Triangle(double base, double h)   implements Shape {}

// Pattern matching switch (Java 21)
double area(Shape s) {
    return switch (s) {
        case Circle    c -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.h();
        case Triangle  t -> 0.5 * t.base() * t.h();
    };
}

var shapes = new Shape[]{
    new Circle(5),
    new Rectangle(4, 6),
    new Triangle(3, 8)
};
for (var shape : shapes) {
    System.out.printf("%-25s area = %.4f%n", shape, area(shape));
}""", "sealed"),

        code("j302-sealed-java", """\
// \u2615 Java Mode \u2014 sealed hierarchy as domain model with visitor-like dispatch
public class SealedDemo {
    sealed interface Expr permits Num, Add, Mul {}
    record Num(int value)           implements Expr {}
    record Add(Expr left, Expr right) implements Expr {}
    record Mul(Expr left, Expr right) implements Expr {}

    static int eval(Expr expr) {
        return switch (expr) {
            case Num(int v)      -> v;
            case Add(var l, var r) -> eval(l) + eval(r);
            case Mul(var l, var r) -> eval(l) * eval(r);
        };
    }

    static String pretty(Expr expr) {
        return switch (expr) {
            case Num(int v)       -> String.valueOf(v);
            case Add(var l, var r) -> "(" + pretty(l) + " + " + pretty(r) + ")";
            case Mul(var l, var r) -> "(" + pretty(l) + " * " + pretty(r) + ")";
        };
    }

    public static void main(String[] args) {
        // (2 + 3) * (4 + 1) = 25
        var expr = new Mul(new Add(new Num(2), new Num(3)),
                           new Add(new Num(4), new Num(1)));
        System.out.println(pretty(expr) + " = " + eval(expr));
    }
}
new SealedDemo().main(null);"""),

        md("j302-h2-concurrency", "## 2. CompletableFuture & Async"),

        code("j302-async-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 async composition with CompletableFuture
import java.util.concurrent.*;

// Simulate async operations
CompletableFuture<String> fetchUser(int id) {
    return CompletableFuture.supplyAsync(() -> {
        // In real code this would be an HTTP call
        return "User-" + id;
    });
}

CompletableFuture<String> fetchEmail(String user) {
    return CompletableFuture.supplyAsync(() -> user + "@example.com");
}

// Chain: fetchUser then fetchEmail
var result = fetchUser(42)
    .thenCompose(user -> fetchEmail(user))
    .thenApply(email -> "Contact: " + email);

System.out.println(result.get());  // blocks until done

// Combine two independent futures
var f1 = CompletableFuture.supplyAsync(() -> "Hello");
var f2 = CompletableFuture.supplyAsync(() -> "World");
var combined = f1.thenCombine(f2, (a, b) -> a + ", " + b + "!");
System.out.println(combined.get());

// Handle errors
var failing = CompletableFuture.<String>failedFuture(new RuntimeException("oops"))
    .exceptionally(ex -> "Recovered: " + ex.getMessage());
System.out.println(failing.get());""", "async"),

        code("j302-async-java", """\
// \u2615 Java Mode \u2014 async pipeline with timeout and error handling
import java.util.concurrent.*;
import java.util.List;

public class AsyncDemo {
    record WeatherData(String city, double temp) {}

    // Simulate calling a weather API
    static CompletableFuture<WeatherData> getWeather(String city) {
        return CompletableFuture.supplyAsync(() -> {
            // Simulate latency
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            double temp = 15 + Math.random() * 20;
            return new WeatherData(city, temp);
        });
    }

    public static void main(String[] args) throws Exception {
        var cities = List.of("London", "Tokyo", "New York", "Sydney");

        // Fetch all in parallel
        var futures = cities.stream()
            .map(AsyncDemo::getWeather)
            .toList();

        // Wait for all
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        System.out.println("Weather report:");
        futures.stream()
            .map(f -> f.getNow(null))
            .sorted(java.util.Comparator.comparingDouble(WeatherData::temp).reversed())
            .forEach(w -> System.out.printf("  %-10s %.1f\u00b0C%n", w.city(), w.temp()));
    }
}
new AsyncDemo().main(null);"""),

        md("j302-h2-vthreads", "## 3. Virtual Threads (Java 21)"),

        code("j302-vthreads-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 Virtual threads (Project Loom)
// Virtual threads are lightweight; you can create thousands cheaply
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// Count how many virtual threads run concurrently
var counter = new AtomicInteger(0);
var tasks   = new java.util.ArrayList<Thread>();

for (int i = 0; i < 1000; i++) {
    final int id = i;
    tasks.add(Thread.ofVirtual().unstarted(() -> {
        counter.incrementAndGet();
        // Simulate I/O wait
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        counter.decrementAndGet();
    }));
}

tasks.forEach(Thread::start);
tasks.forEach(t -> { try { t.join(); } catch (Exception e) {} });

System.out.println("Launched 1000 virtual threads successfully.");
System.out.println("Thread type: " + tasks.get(0).getClass().getSimpleName());

// Executor-based (preferred for pools)
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    var results = new java.util.concurrent.CopyOnWriteArrayList<String>();
    for (int i = 0; i < 5; i++) {
        final int id = i;
        exec.submit(() -> results.add("Task-" + id + " done on " +
            Thread.currentThread().isVirtual()));
    }
}  // auto-shutdown
System.out.println("Virtual thread executor demo complete.");""", "virtual-threads"),

        code("j302-vthreads-java", """\
// \u2615 Java Mode \u2014 structured concurrency with virtual threads
import java.util.concurrent.*;
import java.util.*;

public class VirtualThreadsDemo {
    // Simulate I/O-bound service call
    static String callService(String name, int delayMs) {
        try { Thread.sleep(delayMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return name + " responded in " + delayMs + "ms";
    }

    public static void main(String[] args) throws Exception {
        var start = System.currentTimeMillis();

        // Run 5 "I/O-bound" calls in parallel using virtual threads
        var futures = new ArrayList<Future<String>>();
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            futures.add(exec.submit(() -> callService("Auth",    80)));
            futures.add(exec.submit(() -> callService("User DB", 120)));
            futures.add(exec.submit(() -> callService("Cache",   30)));
            futures.add(exec.submit(() -> callService("Billing", 95)));
            futures.add(exec.submit(() -> callService("Email",   60)));
        }  // waits for all tasks

        var elapsed = System.currentTimeMillis() - start;
        System.out.println("Results (ran in parallel, ~" + elapsed + "ms total):");
        for (var f : futures) System.out.println("  " + f.get());
        // Would take 385ms sequentially, ~120ms in parallel
    }
}
new VirtualThreadsDemo().main(null);"""),

        md("j302-h2-builder", "## 4. Builder Pattern"),

        code("j302-builder-jshell", """\
// \ud83d\udd35 JShell Mode \u2014 Builder pattern for readable object construction
class HttpRequest {
    final String method, url;
    final java.util.Map<String, String> headers;
    final String body;
    final int timeoutMs;

    private HttpRequest(Builder b) {
        method = b.method; url = b.url;
        headers = b.headers; body = b.body; timeoutMs = b.timeoutMs;
    }

    static class Builder {
        String method = "GET", url, body;
        java.util.Map<String,String> headers = new java.util.LinkedHashMap<>();
        int timeoutMs = 5000;

        Builder url(String url)     { this.url = url;     return this; }
        Builder method(String m)    { this.method = m;    return this; }
        Builder header(String k, String v) { headers.put(k,v); return this; }
        Builder body(String b)      { this.body = b;      return this; }
        Builder timeout(int ms)     { this.timeoutMs = ms; return this; }
        HttpRequest build()         { return new HttpRequest(this); }
    }

    @Override public String toString() {
        return method + " " + url + " headers=" + headers.size() +
               " timeout=" + timeoutMs + "ms";
    }
}

var req = new HttpRequest.Builder()
    .url("https://api.example.com/users")
    .method("POST")
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer token123")
    .body("{\"name\": \"Alice\"}")
    .timeout(3000)
    .build();

System.out.println(req);""", "builder"),

        code("j302-builder-java", """\
// \u2615 Java Mode \u2014 Generic Builder with validation and immutable result
import java.util.*;

public class BuilderDemo {
    public record Config(
        String host, int port, int maxConnections,
        Duration connectTimeout, boolean sslEnabled, Map<String, String> properties
    ) {
        public record Duration(int ms) {
            public static Duration ofSeconds(int s) { return new Duration(s * 1000); }
            @Override public String toString() { return ms + "ms"; }
        }

        public static Builder builder(String host) { return new Builder(host); }

        public static class Builder {
            private final String host;
            private int port = 5432;
            private int maxConnections = 10;
            private Duration connectTimeout = Duration.ofSeconds(5);
            private boolean sslEnabled = false;
            private final Map<String, String> props = new LinkedHashMap<>();

            Builder(String host) { this.host = Objects.requireNonNull(host); }
            public Builder port(int p)           { port = p;              return this; }
            public Builder maxConnections(int n) { maxConnections = n;    return this; }
            public Builder timeout(Duration d)   { connectTimeout = d;    return this; }
            public Builder ssl()                 { sslEnabled = true;     return this; }
            public Builder property(String k, String v) { props.put(k,v); return this; }
            public Config build() {
                if (port < 1 || port > 65535) throw new IllegalStateException("Invalid port");
                return new Config(host, port, maxConnections, connectTimeout,
                                  sslEnabled, Collections.unmodifiableMap(props));
            }
        }
    }

    public static void main(String[] args) {
        var config = Config.builder("db.example.com")
            .port(5432)
            .maxConnections(20)
            .timeout(Config.Duration.ofSeconds(10))
            .ssl()
            .property("applicationName", "Venus")
            .build();

        System.out.println("host:       " + config.host());
        System.out.println("port:       " + config.port());
        System.out.println("maxConn:    " + config.maxConnections());
        System.out.println("timeout:    " + config.connectTimeout());
        System.out.println("ssl:        " + config.sslEnabled());
        System.out.println("properties: " + config.properties());
    }
}
new BuilderDemo().main(null);"""),

        md("j302-footer", """\
---

## You've completed Java 302!

You've now covered the full Java spectrum from **basic syntax** to **advanced concurrency** and **modern language features**.

Keep experimenting in Venus Notebooks \u2014 try combining features across notebooks using **copy & paste** or **Browse** to find earlier notebooks.""")
    ],
    "metadata": {},
    "sessionId": None,
    "filename": "java-302.vnb"
}

# Write all three notebooks
for nb in [java101, java202, java302]:
    path = os.path.join(base, nb['filename'])
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(nb, f, indent=2, ensure_ascii=True)
    print('Wrote', nb['filename'], '—', len(nb['cells']), 'cells')

print('Done.')
