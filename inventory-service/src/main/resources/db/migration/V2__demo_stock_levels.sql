-- Bump default demo SKU so repeated happy-path / failure scenarios do not run out of stock.
update inventory_item set available_qty = 50 where sku = 'SKU-LAPTOP' and available_qty < 50;
