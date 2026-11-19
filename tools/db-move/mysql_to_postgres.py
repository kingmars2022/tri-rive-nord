"""Copy every row of the old MySQL database into the new PostgreSQL one.

One-off, for the move off Aiven's retired free MySQL plan (November 2026).
Run by .github/workflows/move-database.yml, which reads both connection
strings from repository secrets, so no password passes through anyone's
chat, terminal history or log.

What it expects:
  * the MySQL database is at V29, the last migration written for MySQL;
  * the PostgreSQL database has been created by the application itself
    (deploy once against it: Flyway builds the schema, V1-V30).

What it does, in ONE PostgreSQL transaction:
  1. empties every application table in PostgreSQL (the reference data
     Flyway seeded is the same as what MySQL holds, and MySQL's copy also
     carries whatever administrators edited since);
  2. copies every row across, ids included, in foreign-key order;
  3. moves each identity sequence past the highest id copied;
  4. compares row counts table by table, and commits only if all match.

If anything fails, nothing in PostgreSQL changes. MySQL is only read.
Prints table names and row counts; never a row.

    MYSQL_URI=mysql://user:pass@host:port/db?ssl-mode=REQUIRED \
    POSTGRES_URI=postgres://user:pass@host:port/db?sslmode=require \
    python3 tools/db-move/mysql_to_postgres.py
"""
import os
import sys
from urllib.parse import parse_qs, unquote, urlparse

import psycopg
import pymysql

# Parents before children. Every table the migrations create, except
# Flyway's own history, which each database keeps for itself.
TABLES = [
    "municipality",
    "municipality_sector",
    "municipality_street",
    "app_user",
    "user_preference",
    "push_subscription",
    "sorting_item",
    "sorting_item_translation",
    "sorting_item_keyword",
    "sorting_item_example",
    "collection_schedule_rule",
    "collection_event",
    "collection_holiday",
    "special_notice",
    "resident_notification",
    "outbox_event",
    "audit_record",
]

LAST_MYSQL_VERSION = 29


def mysql_connect(uri):
    u = urlparse(uri)
    ssl = None if parse_qs(u.query).get("ssl-mode", ["REQUIRED"])[0].upper() == "DISABLED" else {}
    return pymysql.connect(host=u.hostname, port=u.port or 3306, user=unquote(u.username),
                           password=unquote(u.password), database=u.path.lstrip("/"),
                           charset="utf8mb4", ssl=ssl)


def postgres_connect(uri):
    return psycopg.connect(uri, autocommit=False)


def postgres_columns(pg, table):
    """Column name -> data type, without generated columns."""
    rows = pg.execute(
        """select column_name, data_type from information_schema.columns
           where table_schema = 'public' and table_name = %s and is_generated = 'NEVER'
           order by ordinal_position""", (table,)).fetchall()
    return dict(rows)


def check_versions(my, pg):
    with my.cursor() as c:
        c.execute("select max(cast(version as unsigned)) from flyway_schema_history where success = 1")
        mysql_version = c.fetchone()[0]
    if mysql_version != LAST_MYSQL_VERSION:
        sys.exit(f"MySQL is at V{mysql_version}, expected V{LAST_MYSQL_VERSION}; refusing to copy")
    pg_version = pg.execute(
        "select max(cast(version as integer)) from flyway_schema_history where success").fetchone()[0]
    if pg_version is None or pg_version <= LAST_MYSQL_VERSION:
        sys.exit(f"PostgreSQL is at V{pg_version}; deploy the application against it first so Flyway "
                 "builds the schema")
    print(f"MySQL at V{mysql_version}, PostgreSQL at V{pg_version}")


def check_tables(pg):
    present = {r[0] for r in pg.execute(
        "select table_name from information_schema.tables "
        "where table_schema = 'public' and table_type = 'BASE TABLE'").fetchall()}
    missing = set(TABLES) - present
    unknown = present - set(TABLES) - {"flyway_schema_history"}
    if missing or unknown:
        sys.exit(f"table list out of date: missing {sorted(missing)}, not copied {sorted(unknown)}")


def convert(value, data_type):
    if value is None:
        return None
    if data_type == "boolean":
        return bool(value)
    return value


def cast(data_type):
    return "::json" if data_type in ("json", "jsonb") else ""


def copy(my, pg):
    pg.execute("truncate " + ", ".join(TABLES) + " restart identity cascade")
    for table in TABLES:
        columns = postgres_columns(pg, table)
        names = list(columns)
        with my.cursor() as c:
            c.execute(f"select {', '.join(f'`{n}`' for n in names)} from `{table}`")
            rows = c.fetchall()
        if rows:
            placeholders = ", ".join(f"%s{cast(columns[n])}" for n in names)
            with pg.cursor() as cur:
                cur.executemany(
                    f"insert into {table} ({', '.join(names)}) overriding system value "
                    f"values ({placeholders})",
                    [tuple(convert(v, columns[n]) for v, n in zip(row, names)) for row in rows])
        if "id" in columns:
            # setval(..., false) when empty, so the next id is 1 rather than 2.
            pg.execute(f"""select setval(pg_get_serial_sequence('{table}', 'id'),
                                         coalesce(max(id), 1), max(id) is not null) from {table}""")
        print(f"{table:28} {len(rows):6} rows")


def verify(my, pg):
    ok = True
    for table in TABLES:
        with my.cursor() as c:
            c.execute(f"select count(*) from `{table}`")
            mysql_count = c.fetchone()[0]
        pg_count = pg.execute(f"select count(*) from {table}").fetchone()[0]
        if mysql_count != pg_count:
            print(f"MISMATCH {table}: MySQL {mysql_count}, PostgreSQL {pg_count}")
            ok = False
    return ok


def main():
    mysql_uri, postgres_uri = os.environ.get("MYSQL_URI"), os.environ.get("POSTGRES_URI")
    if not mysql_uri or not postgres_uri:
        sys.exit("MYSQL_URI and POSTGRES_URI must both be set")
    my = mysql_connect(mysql_uri)
    with postgres_connect(postgres_uri) as pg:
        check_versions(my, pg)
        check_tables(pg)
        copy(my, pg)
        if not verify(my, pg):
            pg.rollback()
            sys.exit("row counts differ; rolled back, PostgreSQL unchanged")
        pg.commit()
    print("copied and committed")


if __name__ == "__main__":
    main()
