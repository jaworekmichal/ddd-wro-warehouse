package warehouse;

import lombok.AllArgsConstructor;
import warehouse.picklist.FifoRepository;
import warehouse.products.*;
import warehouse.quality.Destroyed;
import warehouse.quality.Locked;
import warehouse.quality.Unlocked;

/**
 * Created by michal on 16.07.2016.
 */
public class EventMappings {

    private ProductStockRepository stocks;
    private FifoRepository fifo;

    public ExternalEvents externalEvents() {
        return new ExternalEvents();
    }

    public ProductStock.EventsContract productStocks() {
        return new ProductStocks();
    }

    @AllArgsConstructor
    public class ExternalEvents {

        public void emit(Locked event) {
            fifo.handle(event.getPaletteLabel().getRefNo(), event);
        }

        public void emit(Unlocked event) {
            fifo.handle(event.getPaletteLabel().getRefNo(), event);
        }

        public void emit(Destroyed event) {
            fifo.handle(event.getPaletteLabel().getRefNo(), event);
        }

        public void emit(Delivered event) {
            stocks.get(event.getPaletteLabel().getRefNo())
                    .ifPresent(productStock -> productStock.delivered(event));
            fifo.handle(event.getPaletteLabel().getRefNo(), event);
        }
    }

    @AllArgsConstructor
    private class ProductStocks implements ProductStock.EventsContract {

        @Override
        public void emit(Registered event) {
            fifo.handle(event.getPaletteLabel().getRefNo(), event);
        }

        @Override
        public void emit(Stored event) {
        }

        @Override
        public void emit(Picked event) {
        }

        @Override
        public void emit(Locked event) {
            fifo.handle(event.getPaletteLabel().getRefNo(), event);
        }
    }

    void dependencies(ProductStockRepository stocks, FifoRepository fifo) {
        this.stocks = stocks;
        this.fifo = fifo;
    }
}
