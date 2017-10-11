package warehouse.picklist;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import warehouse.PaletteLabel;
import warehouse.locations.Location;
import warehouse.products.Delivered;
import warehouse.products.Registered;
import warehouse.quality.Destroyed;
import warehouse.quality.Locked;
import warehouse.quality.Unlocked;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by michal on 15.07.2016.
 */
@AllArgsConstructor
public class Fifo {

    public interface PaletteLocations {
        Location locationOf(PaletteLabel paletteLabel);
    }

    public interface Products {
        PerProduct product(String refNo);
    }

    private final PaletteLocations locations;
    private final Products products;

    public PickList pickList(Order order) {
        PickList.PickListBuilder pickList = PickList.builder();
        for (Order.Item item : order.getItems()) {
            products.product(item.getRefNo())
                    .first(item.getAmount()).forEach(paletteInfo ->
                    pickList.add(paletteInfo.paletteLabel,
                            locations.locationOf(paletteInfo.paletteLabel)));
        }
        return pickList.build();
    }

    protected static class PerProduct {
        private final SortedSet<PaletteInfo> queue = new TreeSet<>(
                Comparator.comparing(PaletteInfo::getReadyAt)
                        .thenComparing(p -> p.getPaletteLabel().getId())
        );

        private final Map<PaletteLabel, PaletteInfo> index = new HashMap<>();

        private List<PaletteInfo> first(int amount) {
            return queue.stream()
                    .filter(PaletteInfo::isAvailable)
                    .limit(amount).collect(Collectors.toList());
        }

        protected void apply(Registered event) {
            add(new PaletteInfo(event.getPaletteLabel(), event.getReadyAt()));
        }

        protected void apply(Locked event) {
            available(event.getPaletteLabel(), false);
        }

        protected void apply(Unlocked event) {
            available(event.getPaletteLabel(), true);
        }

        protected void apply(Delivered event) {
            remove(event.getPaletteLabel());
        }

        protected void apply(Destroyed event) {
            remove(event.getPaletteLabel());
        }

        private void add(PaletteInfo entry) {
            queue.add(entry);
            index.put(entry.getPaletteLabel(), entry);
        }

        private void available(PaletteLabel paletteLabel, boolean available) {
            PaletteInfo entry = index.get(paletteLabel);
            if (entry != null) {
                entry.setAvailable(available);
            }
        }

        private void remove(PaletteLabel paletteLabel) {
            PaletteInfo removed = index.remove(paletteLabel);
            if (removed != null) {
                queue.remove(removed);
            }
        }
    }

    @Data
    @EqualsAndHashCode(of = "paletteLabel")
    protected static class PaletteInfo {
        private final PaletteLabel paletteLabel;
        private final LocalDateTime readyAt;
        private boolean available = true;
    }

}
