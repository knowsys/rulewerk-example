# Rulewerk Example
Example project that shows the use of [Rulewerk](https://github.com/knowsys/rulewerk), and that can be modified to build own projects.

How to run
----------

The project uses Maven to manage dependencies. You can import it into any modern Java IDE, e.g., Eclipse, and run it as a stand-alone Java program.

Alternatively, you can also run it via Maven by executing the following command:
```
mvn compile exec:java
```

If you add another example class and want to run that, you can invoke Maven as follows:
```
mvn compile exec:java -Dexec.mainClass=example.ExampleClassName
```
If not specified, the `mainClass` defaults to `example.RulewerkExample`.

The code requires Java 1.8 or above to run.
