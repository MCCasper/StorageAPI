package wtf.casper.storageapi.impl.fstorage;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import wtf.casper.storageapi.FieldStorage;
import wtf.casper.storageapi.FilterType;
import wtf.casper.storageapi.SortingType;
import wtf.casper.storageapi.cache.Cache;
import wtf.casper.storageapi.cache.CaffeineCache;
import wtf.casper.storageapi.id.exceptions.IdNotFoundException;
import wtf.casper.storageapi.id.utils.IdUtils;
import wtf.casper.storageapi.misc.ISQLStorage;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Log
public abstract class SQLiteFStorage<K, V> implements ISQLStorage<K, V>, FieldStorage<K, V> {

    protected final Class<K> keyClass;
    protected final Class<V> valueClass;
    private final HikariDataSource ds;
    private final String table;
    private Cache<K, V> cache = new CaffeineCache<>(Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build());

    @SneakyThrows
    public SQLiteFStorage(final Class<K> keyClass, final Class<V> valueClass, final File file, String table) {
        if (true) {
            throw new RuntimeException(this.getClass().getSimpleName() + " is not implemented yet!");
        }

        this.keyClass = keyClass;
        this.valueClass = valueClass;
        this.table = table;
        this.ds = new HikariDataSource();
        this.ds.setMaximumPoolSize(20);
        this.ds.setDriverClassName("org.sqlite.JDBC");
        this.ds.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        this.ds.setConnectionTimeout(120000);
        this.ds.setLeakDetectionThreshold(300000);
        this.ds.setAutoCommit(true);
        this.execute(createTableFromObject());
        this.scanForMissingColumns();
    }

    @SneakyThrows
    public SQLiteFStorage(final Class<K> keyClass, final Class<V> valueClass, final String table, final String connection) {
        if (true) {
            throw new RuntimeException(this.getClass().getSimpleName() + " is not implemented yet!");
        }

        this.keyClass = keyClass;
        this.valueClass = valueClass;
        this.table = table;
        this.ds = new HikariDataSource();
        this.ds.setMaximumPoolSize(20);
        this.ds.setDriverClassName("org.sqlite.JDBC");
        this.ds.setJdbcUrl(connection);
        this.ds.setConnectionTimeout(120000);
        this.ds.setLeakDetectionThreshold(300000);
        this.ds.setAutoCommit(true);
        this.execute(createTableFromObject());
        this.scanForMissingColumns();
    }

    @Override
    public HikariDataSource getDataSource() {
        return ds;
    }

    @Override
    public Logger logger() {
        return log;
    }

    @Override
    public String getTable() {
        return table;
    }

    @Override
    public Cache<K, V> cache() {
        return this.cache;
    }

    @Override
    public void cache(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public Class<K> key() {
        return keyClass;
    }

    @Override
    public Class<V> value() {
        return valueClass;
    }

    @Override
    public CompletableFuture<Void> deleteAll() {
        return CompletableFuture.runAsync(() -> {
            execute("DELETE FROM " + this.table);
        });
    }

    @SneakyThrows
    public CompletableFuture<Collection<V>> get(final String field, Object value, FilterType filterType, SortingType sortingType) {
        return CompletableFuture.supplyAsync(() -> {
            final List<V> values = new ArrayList<>();
            if (!filterType.isApplicable(value.getClass())) {
                log.warning("Filter type " + filterType.name() + " is not applicable to " + value.getClass().getSimpleName());
                return values;
            }

            switch (filterType) {
                case EQUALS -> this._equals(field, value, values);
                case CONTAINS -> this._contains(field, value, values);
                case STARTS_WITH -> this.startsWith(field, value, values);
                case ENDS_WITH -> this.endsWith(field, value, values);
                case GREATER_THAN -> this.greaterThan(field, value, values);
                case LESS_THAN -> this.lessThan(field, value, values);
                case GREATER_THAN_OR_EQUAL_TO -> this.greaterThanOrEqualTo(field, value, values);
                case LESS_THAN_OR_EQUAL_TO -> this.lessThanOrEqualTo(field, value, values);
                case NOT_EQUALS -> this.notEquals(field, value, values);
                case NOT_CONTAINS -> this.notContains(field, value, values);
                case NOT_STARTS_WITH -> this.notStartsWIth(field, value, values);
                case NOT_ENDS_WITH -> this.notEndsWith(field, value, values);
            }

            for (V v : values) {
                cache.put((K) IdUtils.getId(valueClass, v), v);
            }

            return values;
        });
    }

    @Override
    public CompletableFuture<V> get(K key) {
        if (cache.getIfPresent(key) != null) {
            return CompletableFuture.completedFuture(cache.getIfPresent(key));
        }
        return getFirst(IdUtils.getIdName(this.valueClass), key);
    }

    @Override
    public CompletableFuture<V> getFirst(String field, Object value, FilterType filterType) {
        return CompletableFuture.supplyAsync(() ->
                this.get(field, value, filterType, SortingType.NONE).join().stream().findFirst().orElse(null)
        );
    }

    @Override
    public CompletableFuture<Void> save(final V value) {
        return CompletableFuture.runAsync(() -> {
            if (this.ds.isClosed()) {
                logger().warning("Could not save " + valueClass.getSimpleName() + " because the data source is closed.");
                return;
            }
            Object id = IdUtils.getId(valueClass, value);
            if (id == null) {
                log.warning("Could not find id field for " + keyClass.getSimpleName());
                return;
            }

            cache.put((K) id, value);

            this.executeUpdate("INSERT INTO " + this.table + " VALUES (" + this.getValues(value, valueClass) + ") ON CONFLICT(" + IdUtils.getIdName(valueClass) + ") DO UPDATE SET " + this.getUpdateValues() + ";");
        });
    }

    @Override
    public CompletableFuture<Void> remove(final V value) {
        return CompletableFuture.runAsync(() -> {
            Field idField;
            try {
                idField = IdUtils.getIdField(valueClass);
            } catch (IdNotFoundException e) {
                throw new RuntimeException(e);
            }
            this.cache.invalidate((K) IdUtils.getId(this.valueClass, value));
            String field = idField.getName();
            this.execute("DELETE FROM " + this.table + " WHERE " + field + " = '" + IdUtils.getId(this.valueClass, value) + "';");
        });
    }

    @Override
    @SneakyThrows
    public CompletableFuture<Void> write() {
        return CompletableFuture.runAsync(() -> {
            this.saveAll(this.cache.asMap().values());
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(this.ds::close);
    }

    @Override
    public CompletableFuture<Collection<V>> allValues() {
        return CompletableFuture.supplyAsync(() -> {
            final List<V> values = new ArrayList<>();
            query("SELECT * FROM " + this.table, statement -> {
            }, resultSet -> {
                try {
                    while (resultSet.next()) {
                        values.add(this.construct(resultSet));
                    }
                } catch (final SQLException e) {
                    e.printStackTrace();
                }
            });

            return values;
        });
    }
}
