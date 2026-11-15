package com.ultimate.inventory.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * MySQL only (local development). Hibernate creates enum fields as MySQL ENUM('A','B') columns, and
 * ddl-auto=update never adds new values to them, so adding a role or status later makes every save
 * of the new value fail ("Data truncated for column ..."). This turns those columns into VARCHAR
 * on start-up, before anything else writes. The stored text is unchanged. Does nothing on Postgres.
 */
@Component
@Order(0)
public class MySqlEnumColumnFix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MySqlEnumColumnFix.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public MySqlEnumColumnFix(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) return;
        }
        List<Map<String, Object>> cols = jdbc.queryForList("""
                select table_name as t, column_name as c, is_nullable as n
                from information_schema.columns
                where table_schema = database() and data_type = 'enum'""");
        for (Map<String, Object> col : cols) {
            String table = (String) col.get("t"), column = (String) col.get("c");
            String nullable = "YES".equals(col.get("n")) ? "null" : "not null";
            // Old CHECK rules on the column (e.g. "type between 0 and 1" from when it was stored as a number)
            // would block the change and are out of date anyway.
            List<String> checks = jdbc.queryForList("""
                    select cc.constraint_name from information_schema.check_constraints cc
                    join information_schema.table_constraints tc
                      on tc.constraint_schema = cc.constraint_schema and tc.constraint_name = cc.constraint_name
                    where cc.constraint_schema = database() and tc.table_name = ? and cc.check_clause like ?""",
                    String.class, table, "%`" + column + "`%");
            for (String check : checks) {
                jdbc.execute("alter table `" + table + "` drop check `" + check + "`");
                log.info("MySQL: dropped out-of-date check {} on {}.{}", check, table, column);
            }
            jdbc.execute("alter table `" + table + "` modify `" + column + "` varchar(50) " + nullable);
            log.info("MySQL: {}.{} changed from ENUM to VARCHAR(50) so new values can be saved", table, column);
        }
    }
}
