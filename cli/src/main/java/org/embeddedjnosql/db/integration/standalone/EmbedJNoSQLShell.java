package org.embeddedjnosql.db.integration.standalone;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;

/**
 * Interactive developer shell for a local EmbedJNoSQL instance.
 *
 * <p>Recovered from the orphaned pre-audit {@code cli/} directory (audit R-25):
 * package corrected to match its path, imports fixed to the real API surface,
 * and a proper entry point added. Commands: help, use, insert, find, count,
 * stats, exit.
 */
public class EmbedJNoSQLShell {

    private final EmbedJNoSQL db;
    private final BufferedReader reader;
    private String currentCollection = "default";

    public EmbedJNoSQLShell(EmbedJNoSQL db) {
        this.db = db;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
    }

    public void start() throws IOException {
        System.out.println("EmbedJNoSQL Shell v1.0");
        System.out.println("Type 'help' for commands, 'exit' to quit");

        while (true) {
            System.out.print("EMBEDJNOSQL:" + currentCollection + "> ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) break; // EOF

            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                break;
            } else if (line.equalsIgnoreCase("help")) {
                printHelp();
            } else if (line.startsWith("use ")) {
                useCollection(line.substring(4).trim());
            } else if (line.startsWith("insert ")) {
                insertDocument(line.substring(7).trim());
            } else if (line.startsWith("find ")) {
                findDocuments(line.substring(5).trim());
            } else if (line.equalsIgnoreCase("count")) {
                showCount();
            } else if (line.equalsIgnoreCase("stats")) {
                showStats();
            } else {
                System.out.println("Unknown command: " + line);
                System.out.println("Type 'help' for available commands");
            }
        }
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  use <name>    - Switch the active collection (creates if absent)");
        System.out.println("  insert <json> - Insert a document into the active collection");
        System.out.println("  find <query>  - 'all', 'field=value', or blank for everything");
        System.out.println("  count         - Document count in the active collection");
        System.out.println("  stats         - Collection statistics");
        System.out.println("  exit|quit     - Exit shell");
    }

    private void useCollection(String collectionName) {
        if (collectionName.isEmpty()) {
            System.out.println("Usage: use <collection>");
            return;
        }
        db.documentCollection(collectionName);
        currentCollection = collectionName;
        System.out.println("Using collection: " + collectionName);
    }

    private void insertDocument(String json) {
        try {
            Document doc = Document.fromJson(json);
            DocumentCollection collection = db.documentCollection(currentCollection);
            Document saved = collection.insert(doc);
            System.out.println("Inserted with id: " + saved.id());
        } catch (Exception e) {
            System.out.println("Error inserting document: " + e.getMessage());
        }
    }

    private void findDocuments(String queryStr) {
        try {
            DocumentCollection collection = db.documentCollection(currentCollection);
            Query query;

            if (queryStr.isEmpty() || queryStr.equalsIgnoreCase("all")) {
                query = Query.all();
            } else {
                // Treat as a simple field=value equality unless JSON-ish
                String[] parts = queryStr.split("=", 2);
                if (parts.length == 2) {
                    String value = parts[1].trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    query = Query.eq(parts[0].trim(), value);
                } else {
                    query = Query.all();
                }
            }

            var results = collection.find(query);
            System.out.println("Found " + results.size() + " documents:");
            for (Document doc : results) {
                System.out.println("  " + doc.toJson());
            }
        } catch (Exception e) {
            System.out.println("Error executing query: " + e.getMessage());
        }
    }

    private void showCount() {
        DocumentCollection collection = db.documentCollection(currentCollection);
        System.out.println("Document count: " + collection.count());
    }

    private void showStats() {
        DocumentCollection collection = db.documentCollection(currentCollection);
        var stats = collection.stats();
        System.out.println("Collection stats:");
        for (var entry : stats.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar embed-jnosql-cli.jar [data-dir]");
        System.out.println("  data-dir    Directory of the local EmbedJNoSQL instance (default: data)");
        System.out.println("  --help      Show this help");
    }

    /**
     * Resolves the data directory from the command line. Unsupported options are
     * rejected instead of silently ignored, so a mistyped flag cannot quietly
     * start the shell against the default directory.
     *
     * @throws IllegalArgumentException when an unknown option or extra argument is given
     */
    static String parseDataDir(String[] args) {
        String dataDir = null;
        for (String arg : args) {
            if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            }
            if (dataDir != null) {
                throw new IllegalArgumentException("Unexpected extra argument: " + arg);
            }
            dataDir = arg;
        }
        return dataDir == null ? "data" : dataDir;
    }

    public static void main(String[] args) throws IOException {
        for (String arg : args) {
            if ("--help".equals(arg)) {
                printUsage();
                return;
            }
        }
        String dataDir;
        try {
            dataDir = parseDataDir(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }
        var db = EmbedJNoSQL.create(EmbedJNoSQLConfig.builder()
                .persistTo(dataDir)
                .buildConfig());
        try {
            new EmbedJNoSQLShell(db).start();
        } finally {
            db.close();
        }
    }
}
