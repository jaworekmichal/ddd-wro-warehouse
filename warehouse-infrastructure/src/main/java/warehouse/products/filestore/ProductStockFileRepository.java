package warehouse.products.filestore;

import lombok.Data;
import tools.AgentQueue;
import tools.MultiMethod;
import warehouse.EventMappings;
import warehouse.Persistence;
import warehouse.locations.BasicLocationPicker;
import warehouse.products.*;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by michal on 13.07.2016.
 */
public class ProductStockFileRepository implements ProductStockExtendedRepository {

    @Data
    public static class EventEntry {
        private final UUID id;
        private final LocalDateTime created;
        private final String refNo;
        private final String type;
        private final String json;
    }

    private static final MultiMethod<ProductStock, Void> productStock$handle =
            MultiMethod.in(ProductStock.class).method("apply")
                    .lookup(MethodHandles.lookup())
                    .onMissingHandler(Exception::printStackTrace);

    // caches
    private final Map<String, ProductStockAgent> products = new ConcurrentHashMap<>();

    // repository dependencies
    private final FileStore store = new FileStore();

    // aggregate dependencies
    private final PaletteValidator validator;
    private final BasicLocationPicker locationPicker;
    private final ProductStock.EventsContract events;
    private final Clock clock;

    public ProductStockFileRepository(EventMappings mappings) {
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
            for (Object event : history) {
                try {
                    productStock$handle.call(stock, event);
                } catch (Throwable throwable) {
                    // stock <refNo> cannot replay event <event> cause <throwable>
                    throwable.printStackTrace();
                }
            }
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
        EventEntry entry = new EventEntry(
                UUID.randomUUID(), LocalDateTime.now(), refNo, alias, json);
        store.append(refNo, entry);
    }

    protected List<Object> retrieve(String refNo) {
        List<ProductStockFileRepository.EventEntry> history = store.readAll(refNo, EventEntry.class);
        return history.stream()
                .map(entry -> Persistence.serialization.deserialize(entry.getJson(), entry.getType()))
                .collect(Collectors.toList());
    }
}
