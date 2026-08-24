from typing import List
from src.config import STOCK_FILE, PRODUCTS_FILE
from src.file_manager import read_all_lines, write_all_lines
from src.models import StockItem

BATCH_SIZE = 10

def supplier_deliver_item(product_id: int, quantity: int) -> None:
    print(f"[Supplier] Delivering {quantity} units of Product ID {product_id} to Warehouse...")
    lines = read_all_lines(STOCK_FILE)
    stocks: List[StockItem] = []
    found = False

    for line in lines:
        p_id, s_qty, w_qty = map(int, line.split(","))
        if p_id == product_id:
            w_qty += quantity
            found = True
        stocks.append(StockItem(p_id, s_qty, w_qty))

    if not found:
        stocks.append(StockItem(product_id, 0, quantity))

    write_all_lines(STOCK_FILE, [s.to_csv() for s in stocks])

def supplier_order_item(product_id: int) -> None:
    print(f"[Warehouse] Out of stock. Ordering batch of {BATCH_SIZE} from Supplier...")
    supplier_deliver_item(product_id, BATCH_SIZE)

def check_and_fulfill_stock(product_id: int) -> bool:
    lines = read_all_lines(STOCK_FILE)
    stocks: List[StockItem] = []
    found = False
    fulfilled = False

    for line in lines:
        p_id, s_qty, w_qty = map(int, line.split(","))
        if p_id == product_id:
            found = True
            if s_qty > 0:
                s_qty -= 1
                fulfilled = True
            else:
                print("[Store] Product out of stock in Store. Checking Warehouse...")
                if w_qty <= 0:
                    supplier_order_item(product_id)
                    return check_and_fulfill_stock(product_id)
                w_qty -= 1
                s_qty += (BATCH_SIZE - 1)
                print("[Store] Transferred batch from Warehouse to Store.")
                fulfilled = True
        stocks.append(StockItem(p_id, s_qty, w_qty))

    if not found:
        print(f"[System] Initializing stock entry for Product ID {product_id}...")
        supplier_order_item(product_id)
        return check_and_fulfill_stock(product_id)

    write_all_lines(STOCK_FILE, [s.to_csv() for s in stocks])
    return fulfilled

def request_repair() -> None:
    print("\n-----------------------------")
    print("repair handled")
    print("-----------------------------\n")

def process_billing(product_ids: List[int]) -> None:
    prod_lines = read_all_lines(PRODUCTS_FILE)
    total = 0.0

    print("\n==================================")
    print("          RECEIPT / BILL          ")
    print("==================================")
    print(f"{'ID':<8} {'Name':<15} {'Price':<10}")
    print("----------------------------------")

    for p_id in product_ids:
        if check_and_fulfill_stock(p_id):
            for line in prod_lines:
                id_str, name, cat, price_str = line.split(",")
                if int(id_str) == p_id:
                    price = float(price_str)
                    total += price
                    print(f"{p_id:<8} {name:<15} ${price:.2f}")
                    break

    print("----------------------------------")
    print(f"TOTAL AMOUNT: ${total:.2f}")
    print("==================================\n")