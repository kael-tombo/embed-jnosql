#!/usr/bin/env bash
# Generates 20 standalone Maven demo projects under demo/demos/NN-name.
# Each project: pom.xml + one main class showing the API + one JUnit test asserting the behavior.
set -euo pipefail
cd "$(dirname "$0")"   # demo/

BASE=org.embeddedjnosql.db.demo

POM() {
cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.embeddedjnosql.db.demo.demos</groupId>
    <artifactId>$1</artifactId>
    <version>1.0.0</version>
    <name>EmbedJNoSQL Demo \u2014 $2</name>
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>io.github.kael-tombo</groupId>
            <artifactId>embed-jnosql-core</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
EOF
}

mk() { # mk <slugNN> <ClassName> <Title> <MainBodyFile> <TestBodyFile>
  local slug="$1" cls="$2" title="$3" mainf="$4" testf="$5"
  local dir="demos/$slug" pkg
  pkg=$(echo "$slug" | sed 's/^[0-9]*-//; s/-//g')
  mkdir -p "$dir/src/main/java/org/embeddedjnosql/db/demo/demos/$pkg" \
           "$dir/src/test/java/org/embeddedjnosql/db/demo/demos/$pkg"
  POM "$slug" "$title" > "$dir/pom.xml"
  cat "$mainf" > "$dir/src/main/java/org/embeddedjnosql/db/demo/demos/$pkg/$cls.java"
  cat "$testf" > "$dir/src/test/java/org/embeddedjnosql/db/demo/demos/$pkg/${cls}Test.java"
}

S() {
cat
}

# ---------- 01 document-crud ----------
mk "01-document-crud" "DocumentCrudDemo" "Document CRUD — insert, findById, update, delete" main01.txt test01.txt

# ---------- 02 native-query-predicates ----------
mk "02-native-query-predicates" "NativeQueryDemo" "Native queries — predicates, sorting, paging" main02.txt test02.txt

# ---------- 03 nested-fields ----------
mk "03-nested-field-queries" "NestedFieldDemo" "Nested field (dotted-path) queries" main03.txt test03.txt

# ---------- 04 secondary-indexes ----------
mk "04-secondary-indexes" "SecondaryIndexDemo" "Secondary indexes — create, list, drop" main04.txt test04.txt

# ---------- 05 ttl-expiry ----------
mk "05-ttl-expiry" "TtlExpiryDemo" "TTL — document, key-value, and column expiry" main05.txt test05.txt

# ---------- 06 key-value ----------
mk "06-key-value-buckets" "KeyValueDemo" "Key-Value buckets — put/get with TTL, counters" main06.txt test06.txt

# ---------- 07 redis-lists ----------
mk "07-redis-style-lists" "RedisListDemo" "Redis-style lists — queues and stacks" main07.txt test07.txt

# ---------- 08 redis-sets ----------
mk "08-redis-style-sets" "RedisSetDemo" "Redis-style sets — tags, membership, ops" main08.txt test08.txt

# ---------- 09 redis-hashes ----------
mk "09-redis-style-hashes" "RedisHashDemo" "Redis-style hashes — field maps and counters" main09.txt test09.txt

# ---------- 10 wide-columns ----------
mk "10-wide-column-families" "WideColumnDemo" "Wide-Column families — rows, columns, TTL" main10.txt test10.txt

# ---------- 11 transactions ----------
mk "11-mvcc-transactions" "MvccTransactionDemo" "MVCC transactions — commit and rollback" main11.txt test11.txt

# ---------- 12 aggregation ----------
mk "12-aggregation-pipeline" "AggregationDemo" "Aggregation — group, sum, avg, min, max in the JVM" main12.txt test12.txt

# ---------- 13 sql-to-nosql-migration ----------
mk "13-sql-to-nosql-migration" "SqlMigrationDemo" "SQL to NoSQL — migrating a schema without data loss" main13.txt test13.txt

# ---------- 14 event-bus ----------
mk "14-event-bus" "EventBusDemo" "Event bus — subscribe to database lifecycle" main14.txt test14.txt

# ---------- 15 metrics ----------
mk "15-metrics" "MetricsDemo" "Metrics — ops, transactions, snapshots" main15.txt test15.txt

# ---------- 16 backup-restore ----------
mk "16-backup-restore" "BackupDemo" "Backup and restore - gzip snapshot round-trip" main16.txt test16.txt

# ---------- 17 cdc change feed ----------
mk "17-cdc-change-feed" "CdcChangeFeedDemo" "CDC change feed — typed events for insert, update, delete" main17.txt test17.txt

# ---------- 18 text-search ----------
mk "18-text-search" "TextSearchDemo" "Text search — fuzzy matching with scoring" main18.txt test18.txt

# ---------- 19 vectors ----------
mk "19-vector-search" "VectorSearchDemo" "Vector search — HNSW k-NN (experimental)" main19.txt test19.txt

# ---------- 20 durability ----------
mk "20-durability-recovery" "DurabilityDemo" "Durability — WAL recovery across restarts" main20.txt test20.txt

echo "Generated $(ls demos | wc -l) demo projects under demos/"
