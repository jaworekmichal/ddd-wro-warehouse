package warehouse.products;

import lombok.Data;
import org.sql2o.ResultSetIterable;
import org.sql2o.Sql2o;
import tools.AgentQueue;
import tools.EventsApplier;
import warehouse.EventMappings;
import warehouse.Persistence;
import warehouse.locations.BasicLocationPicker;
import warehouse.quality.Locked;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by michal on 13.07.2016.
 */
public class ProductStockDatabaseRepository implements ProductStockExtendedRepository {

    @Data
    private static class ProductStockHistoryEvent {
        private final long id;
        private final Instant created;
        private final String refNo;
        private final String type;
        private final String content;
    }

    private static final EventsApplier<ProductStock> applier =
            EventsApplier.forType(ProductStock.class)
                    .define(Registered.class, ProductStock::apply)
                    .define(Stored.class, ProductStock::apply)
                    .define(Picked.class, ProductStock::apply)
                    .define(Locked.class, ProductStock::apply)
                    .onMissingDefinitionSkip();

    // caches
    private final Map<String, ProductStockAgent> products = new ConcurrentHashMap<>();

    // repository dependencies
    private final Sql2o sql2o;

    // aggregate dependencies
    private final PaletteValidator validator;
    private final BasicLocationPicker locationPicker;
    private final ProductStock.EventsContract events;
    private final Clock clock;

    public ProductStockDatabaseRepository(EventMappings mappings, Sql2o sql2o) {
        this.sql2o = sql2o;
        this.validator = new PaletteValidator();
        this.locationPicker = new BasicLocationPicker(Collections.emptyMap());
        this.events = new ProductStockEventsHandler(this, mappings.productStocks());
        this.clock = Clock.systemDefaultZone();
    }

    @Override
    public Optional<ProductStockAgent> get(String refNo) {
        return Optional.ofNullable(products.computeIfAbsent(refNo, key -> {
            List<Object> history = retrieve(refNo);
            if (history.isEmpty()) {
                return null;
            }
            ProductStock stock = new ProductStock(refNo, validator, locationPicker, events, clock);
            applier.apply(stock, history);
            return new ProductStockAgent(stock, new AgentQueue());
        }));
    }

    public List<Object> readEvents(String refNo) {
        return retrieve(refNo);
    }

    @Override
    public void persist(String refNo, Object event) {
        String json = Persistence.serialization.serialize(event);
        String alias = Persistence.serialization.of(event.getClass()).getAlias();

        try (org.sql2o.Connection connection = sql2o.beginTransaction()) {
            connection.createQuery(
                    "insert into warehouse.ProductStockHistory(refNo, type, content) " +
                            "values (:refNo, :type, cast(:content AS json))")
                    .addParameter("refNo", refNo)
                    .addParameter("type", alias)
                    .addParameter("content", json)
                    .executeUpdate();
            connection.commit();
        }
    }

    protected List<Object> retrieve(String refNo) {
        try (ResultSetIterable<ProductStockHistoryEvent> result = sql2o.open().createQuery(
                "select * from warehouse.ProductStockHistory where refNo = :refNo order by id")
                .addParameter("refNo", refNo)
                .executeAndFetchLazy(ProductStockHistoryEvent.class)) {
            return StreamSupport.stream(result.spliterator(), false)
                    .map(entry -> Persistence.serialization.deserialize(entry.getContent(), entry.getType()))
                    .collect(Collectors.toList());
        }
    }
}
