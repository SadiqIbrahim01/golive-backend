package com.golivebackend.database.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Explicit database layer configuration.
 *
 * WHY THIS CLASS EXISTS:
 * Spring Boot would auto-configure all of this from application.yml.
 * We choose to be explicit because:
 *   1. The configuration is visible and documented — no magic
 *   2. We can add startup validation logic
 *   3. Easier to reason about during debugging
 *   4. Gives us a clear place to add read replicas later
 *
 * ANNOTATIONS:
 *   @Configuration       → this class produces Spring beans (via @Bean methods)
 *   @EnableJpaRepositories → activates Spring Data JPA repositories in the
 *                            specified base package. Without this, your
 *                            @Repository interfaces won't be detected.
 *   @EnableTransactionManagement → activates @Transactional support across
 *                                  the application. Without this, @Transactional
 *                                  annotations are silently ignored.
 */
@Configuration
@EnableJpaRepositories(
        /*
         * Tell Spring Data exactly where to scan for repository interfaces.
         * Being explicit here means Spring doesn't scan your entire codebase —
         * faster startup and no accidental repository detection in wrong packages.
         */
        basePackages = "com.golivebackend"
)
@EnableTransactionManagement
public class DatabaseConfig {

    /*
     * @Value injects values from application.yml / environment variables.
     * The format is: @Value("${yaml.key.path}")
     *
     * These map directly to what you defined in application.yml:
     *   spring.datasource.url → ${spring.datasource.url}
     *
     * WHY NOT @ConfigurationProperties?
     * For a small number of values, @Value is clear and direct.
     * When you have many related values (like livekit.*), we'll use
     * @ConfigurationProperties — which we'll do in Step 3.
     */
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${spring.datasource.hikari.maximum-pool-size:5}")
    private int maxPoolSize;
    // The ':5' after the key is a default value.
    // If the property is missing, Spring uses 5 instead of throwing an error.

    @Value("${spring.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:300000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.pool-name:GoLiveHikariPool}")
    private String poolName;

    @Value("${spring.jpa.hibernate.ddl-auto:validate}")
    private String ddlAuto;

    @Value("${spring.jpa.show-sql:false}")
    private boolean showSql;

    /**
     * DATASOURCE BEAN — The connection pool.
     *
     * HikariCP is the fastest, most battle-tested JDBC connection pool
     * available for Java. Spring Boot uses it by default.
     *
     * What a connection pool does:
     * Opening a raw database connection is expensive (~100ms).
     * A pool pre-opens N connections and keeps them alive.
     * When your code needs the DB, it borrows a connection, uses it,
     * and returns it — no teardown cost.
     *
     * @Bean tells Spring: "call this method and register what it returns
     * as a managed bean." Other beans can then declare DataSource as a
     * constructor parameter and Spring injects this instance.
     */
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(datasourceUrl);
        config.setUsername(datasourceUsername);
        config.setPassword(datasourcePassword);
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setPoolName(poolName);

        /*
         * DRIVER CLASS: We declare this explicitly.
         * HikariCP can auto-detect from the JDBC URL but explicit is safer —
         * if the PostgreSQL driver JAR is missing from the classpath,
         * you get a clear ClassNotFoundException here rather than a
         * cryptic failure later.
         */
        config.setDriverClassName("org.postgresql.Driver");

        /*
         * CONNECTION VALIDATION QUERY:
         * Before lending a connection to your code, HikariCP runs this
         * query to confirm the connection is still alive.
         * PostgreSQL supports the lightweight "SELECT 1" for this.
         *
         * Without this: your code might get a dead connection and fail
         * mid-request with a confusing error.
         */
        config.setConnectionTestQuery("SELECT 1");

        /*
         * CONNECTION INIT SQL:
         * Runs once when a connection is first created.
         * We set the timezone explicitly so all DB timestamps are UTC,
         * regardless of the server's local timezone setting.
         * This prevents a whole class of subtle timezone bugs.
         */
        config.setConnectionInitSql("SET TIME ZONE 'UTC'");

        return new HikariDataSource(config);
    }

    /**
     * ENTITY MANAGER FACTORY — The JPA core.
     *
     * The EntityManagerFactory is the heavyweight object that:
     *   - Scans your @Entity classes and maps them to DB tables
     *   - Manages the first-level cache (persistence context)
     *   - Handles object lifecycle (managed, detached, removed states)
     *
     * Spring creates one EntityManagerFactory per application.
     * Spring Data JPA repositories use it internally — you never
     * interact with it directly in normal code.
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource
            /*
             * Spring injects the DataSource bean we defined above.
             * This is constructor/method injection — the correct way.
             * We never call dataSource() directly — Spring manages it.
             */
    ) {
        LocalContainerEntityManagerFactoryBean factory =
                new LocalContainerEntityManagerFactoryBean();

        factory.setDataSource(dataSource);

        /*
         * Where to scan for @Entity classes.
         * Every class annotated with @Entity in com.golive and below
         * will be picked up and mapped to the database.
         */
        factory.setPackagesToScan("com.golivebackend");

        /*
         * HibernateJpaVendorAdapter bridges Spring's JPA abstraction
         * with Hibernate's concrete implementation.
         * We're using Hibernate as our JPA provider — this adapter
         * translates Spring's configuration into Hibernate-specific config.
         */
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        factory.setJpaVendorAdapter(vendorAdapter);

        factory.setJpaProperties(hibernateProperties());

        return factory;
    }

    /**
     * TRANSACTION MANAGER — Coordinates @Transactional.
     *
     * When your service method is annotated with @Transactional:
     *   1. Spring intercepts the call (via AOP proxy)
     *   2. Asks the TransactionManager to open a transaction
     *   3. Runs your method
     *   4. Commits if successful, rolls back if an exception is thrown
     *
     * Without this bean, @Transactional does nothing.
     */
    @Bean
    public PlatformTransactionManager transactionManager(
            LocalContainerEntityManagerFactoryBean entityManagerFactory
    ) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(
                entityManagerFactory.getObject()
        );
        return transactionManager;
    }

    /**
     * Hibernate-specific properties.
     * Extracted into a private method to keep entityManagerFactory() clean.
     * These correspond to the spring.jpa.properties.* keys in application.yml.
     */
    private Properties hibernateProperties() {
        Properties properties = new Properties();

        /*
         * DDL auto strategy — controls schema management.
         * Value comes from application.yml, which differs per profile:
         *   local: create-drop
         *   prod:  validate
         */
        properties.setProperty(
                "hibernate.hbm2ddl.auto",
                ddlAuto
        );

        /*
         * Log generated SQL. Useful in local, noisy in production.
         * Value controlled by spring.jpa.show-sql in application.yml.
         */
        properties.setProperty(
                "hibernate.show_sql",
                String.valueOf(showSql)
        );

        /*
         * Format logged SQL across multiple lines for readability.
         * Only relevant when show_sql is true.
         */
        properties.setProperty(
                "hibernate.format_sql",
                "true"
        );

        /*
         * Batch fetching: when Hibernate lazy-loads a collection,
         * it fetches up to 20 items per query instead of 1.
         * Dramatically reduces N+1 query problems without
         * requiring explicit JOIN FETCH everywhere.
         */
        properties.setProperty(
                "hibernate.default_batch_fetch_size",
                "20"
        );

        /*
         * Prevents the "open session in view" anti-pattern.
         * Forces all DB access to happen within a @Transactional boundary.
         * If you access a lazy collection outside a transaction,
         * you get a clear LazyInitializationException — not a silent
         * extra query that degrades performance.
         */
        properties.setProperty(
                "hibernate.enable_lazy_load_no_trans",
                "false"
        );

        return properties;
    }
}